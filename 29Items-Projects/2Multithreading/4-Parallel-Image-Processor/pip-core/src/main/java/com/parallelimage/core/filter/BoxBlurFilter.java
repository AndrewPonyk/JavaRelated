package com.parallelimage.core.filter;

import com.parallelimage.core.fork.CancellationToken;
import com.parallelimage.core.model.Tile;
import com.parallelimage.core.pipeline.TileKernel;
import com.parallelimage.core.util.Preconditions;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Box blur with a square kernel of side {@code 2 * radius + 1}.
 *
 * <h2>Why this kernel is the interesting one</h2>
 * It is the first filter that reads <em>outside</em> its own tile. That is exactly what the
 * {@link TileKernel} contract permits and why it is written the way it is: the halo is read from the
 * <strong>immutable source</strong> image, never from the partially-written destination. If a tile
 * read its neighbour's <em>output</em> it would be racing with a sibling task, and the result would be
 * visible as faint seams along tile boundaries — nondeterministically, on some machines only.
 * Recomputing the halo from source costs a little redundant work and buys determinism: parallelism 1
 * and parallelism 16 produce byte-identical output, which is the property the integration test suite
 * asserts (TECH-NOTES §3.2).
 *
 * <h2>Algorithm</h2>
 * Separable two-pass with a sliding window, so cost is O(1) per pixel rather than O(radius²):
 * <ol>
 *   <li>horizontal pass into a tile-local scratch band, {@code height + 2·radius} rows tall so the
 *       vertical pass has its own halo already blurred;</li>
 *   <li>vertical pass from the scratch band into the destination tile.</li>
 * </ol>
 * The scratch band stores <em>averages</em> packed back into an {@code int} rather than raw sums. That
 * loses at most half a level per channel — invisible, and deterministic — while keeping the scratch
 * band one array instead of four.
 *
 * <p><strong>All scratch is local to {@link #apply}.</strong> A field would be shared by every worker
 * and corrupt output instantly; this is the single most common way to break the kernel contract
 * (TECH-NOTES §3.6 A9). The scratch arrays themselves are borrowed from {@link TileBufferPool} and
 * released before {@code apply} returns rather than freshly allocated every call — this is the
 * kernel Level 2 calls most often per pixel of work, so its per-leaf {@code int[]} churn was the
 * one worth pooling.
 */
public final class BoxBlurFilter implements TileKernel {

    private final int radius;
    private final CancellationToken token;

    /**
     * Creates a blur of the given radius, cancellable between output rows.
     *
     * @param radius kernel radius in pixels, 1..64
     * @param token  polled once per output row; a 64-pixel radius over a large tile is long enough
     *               that a per-task check alone would leave Cancel feeling unresponsive.
     *               {@code null} means never cancelled.
     */
    public BoxBlurFilter(int radius, CancellationToken token) {
        this.radius = Preconditions.requirePositive(radius, "radius");
        this.token = token == null ? CancellationToken.NONE : token;
    }

    public BoxBlurFilter(int radius) {
        this(radius, CancellationToken.NONE);
    }

    @Override
    public void apply(BufferedImage source, BufferedImage target, Tile tile) {
        final int[] src = Pixels.data(source);
        final int[] dst = Pixels.data(target);
        final int srcStride = Pixels.stride(source);
        final int dstStride = Pixels.stride(target);
        final int srcOffset = Pixels.offset(source);
        final int dstOffset = Pixels.offset(target);
        final int srcWidth = source.getWidth();
        final int srcHeight = source.getHeight();

        final int tx = tile.x();
        final int ty = tile.y();
        final int tw = tile.width();
        final int th = tile.height();
        final int r = radius;
        final int window = 2 * r + 1;
        final int bandRows = th + 2 * r;

        // Sums fit comfortably in an int: 129 taps * 255 = 32 895.
        final int[] band = TileBufferPool.borrow(tw * bandRows);
        // Column accumulators kept as rows so both loops stay row-major and cache-friendly.
        final int[] accA = TileBufferPool.borrow(tw);
        final int[] accR = TileBufferPool.borrow(tw);
        final int[] accG = TileBufferPool.borrow(tw);
        final int[] accB = TileBufferPool.borrow(tw);
        try {
            // ---- pass 1: horizontal, source -> band ----
            for (int j = 0; j < bandRows; j++) {
                int sy = Pixels.clampCoord(ty - r + j, srcHeight);
                int srcRow = srcOffset + sy * srcStride;

                int sa = 0;
                int sr = 0;
                int sg = 0;
                int sb = 0;
                for (int dx = -r; dx <= r; dx++) {
                    int argb = src[srcRow + Pixels.clampCoord(tx + dx, srcWidth)];
                    sa += argb >>> 24;
                    sr += (argb >> 16) & 0xFF;
                    sg += (argb >> 8) & 0xFF;
                    sb += argb & 0xFF;
                }

                int bandRow = j * tw;
                for (int i = 0; i < tw; i++) {
                    band[bandRow + i] =
                            Pixels.pack(sa / window, sr / window, sg / window, sb / window);
                    if (i + 1 < tw) {
                        int leaving = src[srcRow + Pixels.clampCoord(tx + i - r, srcWidth)];
                        int entering = src[srcRow + Pixels.clampCoord(tx + i + r + 1, srcWidth)];
                        sa += (entering >>> 24) - (leaving >>> 24);
                        sr += ((entering >> 16) & 0xFF) - ((leaving >> 16) & 0xFF);
                        sg += ((entering >> 8) & 0xFF) - ((leaving >> 8) & 0xFF);
                        sb += (entering & 0xFF) - (leaving & 0xFF);
                    }
                }
            }

            // ---- pass 2: vertical, band -> destination tile ----
            // Borrowed accumulators may hold a previous caller's values; += below needs them
            // cleared first (a freshly allocated int[] would not).
            Arrays.fill(accA, 0, tw, 0);
            Arrays.fill(accR, 0, tw, 0);
            Arrays.fill(accG, 0, tw, 0);
            Arrays.fill(accB, 0, tw, 0);
            for (int j = 0; j < window; j++) {
                int bandRow = j * tw;
                for (int i = 0; i < tw; i++) {
                    int argb = band[bandRow + i];
                    accA[i] += argb >>> 24;
                    accR[i] += (argb >> 16) & 0xFF;
                    accG[i] += (argb >> 8) & 0xFF;
                    accB[i] += argb & 0xFF;
                }
            }

            for (int j = 0; j < th; j++) {
                token.throwIfCancelled();

                int dstRow = dstOffset + (ty + j) * dstStride + tx;
                for (int i = 0; i < tw; i++) {
                    dst[dstRow + i] = Pixels.pack(
                            accA[i] / window, accR[i] / window, accG[i] / window, accB[i] / window);
                }
                if (j + 1 < th) {
                    int leavingRow = j * tw;
                    int enteringRow = (j + window) * tw;
                    for (int i = 0; i < tw; i++) {
                        int leaving = band[leavingRow + i];
                        int entering = band[enteringRow + i];
                        accA[i] += (entering >>> 24) - (leaving >>> 24);
                        accR[i] += ((entering >> 16) & 0xFF) - ((leaving >> 16) & 0xFF);
                        accG[i] += ((entering >> 8) & 0xFF) - ((leaving >> 8) & 0xFF);
                        accB[i] += (entering & 0xFF) - (leaving & 0xFF);
                    }
                }
            }
        } finally {
            TileBufferPool.release(band);
            TileBufferPool.release(accA);
            TileBufferPool.release(accR);
            TileBufferPool.release(accG);
            TileBufferPool.release(accB);
        }
    }

    public int radius() {
        return radius;
    }

    @Override
    public String name() {
        return "blur(r=" + radius + ")";
    }
}
