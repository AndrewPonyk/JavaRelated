package com.parallelimage.core.filter;

import com.parallelimage.core.model.Tile;
import com.parallelimage.core.pipeline.TileKernel;
import com.parallelimage.core.util.Preconditions;
import java.awt.image.BufferedImage;

/**
 * 3&times;3 cross-shaped sharpen (discrete Laplacian added back to the original).
 *
 * <pre>
 *          -a
 *   -a   1+4a   -a          weights sum to 1, so overall brightness is preserved
 *          -a
 * </pre>
 *
 * <p>A one-pixel halo, read from the immutable source exactly as in {@link BoxBlurFilter}. Edge
 * pixels clamp the sample coordinate rather than skipping the filter, which avoids the unsharpened
 * one-pixel border that a naive "start at 1, end at width-1" loop leaves behind — a border that
 * happens to be invisible in a whole-image implementation and glaringly visible as a grid once the
 * image is tiled.
 *
 * <p>Weights are converted to fixed point once in the constructor: the inner loop then does integer
 * multiply-accumulate only. Stateless and shared across workers.
 */
public final class SharpenFilter implements TileKernel {

    private static final int FRACTION_BITS = 12;
    private static final int ONE = 1 << FRACTION_BITS;

    private final double amount;
    private final int centreWeight;
    private final int neighbourWeight;

    /**
     * Creates a sharpen kernel, precomputing its fixed-point weights from {@code amount}.
     *
     * @param amount 0.0 = identity, 1.0 = standard, up to 5.0 for aggressive
     */
    public SharpenFilter(double amount) {
        this.amount = Preconditions.requireInRange(amount, 0.0d, 5.0d, "amount");
        this.centreWeight = (int) Math.round((1.0d + 4.0d * amount) * ONE);
        this.neighbourWeight = (int) Math.round(-amount * ONE);
    }

    @Override
    public void apply(BufferedImage source, BufferedImage target, Tile tile) {
        final int[] src = Pixels.data(source);
        final int[] dst = Pixels.data(target);
        final int srcStride = Pixels.stride(source);
        final int dstStride = Pixels.stride(target);
        final int srcOffset = Pixels.offset(source);
        final int dstOffset = Pixels.offset(target);
        final int width = source.getWidth();
        final int height = source.getHeight();

        final int maxX = tile.maxX();
        final int maxY = tile.maxY();

        for (int y = tile.y(); y < maxY; y++) {
            int rowAbove = srcOffset + Pixels.clampCoord(y - 1, height) * srcStride;
            int rowCentre = srcOffset + y * srcStride;
            int rowBelow = srcOffset + Pixels.clampCoord(y + 1, height) * srcStride;
            int dstRow = dstOffset + y * dstStride;

            for (int x = tile.x(); x < maxX; x++) {
                int left = Pixels.clampCoord(x - 1, width);
                int right = Pixels.clampCoord(x + 1, width);

                int centre = src[rowCentre + x];
                int up = src[rowAbove + x];
                int down = src[rowBelow + x];
                int west = src[rowCentre + left];
                int east = src[rowCentre + right];

                int r = convolve(centre >> 16, up >> 16, down >> 16, west >> 16, east >> 16);
                int g = convolve(centre >> 8, up >> 8, down >> 8, west >> 8, east >> 8);
                int b = convolve(centre, up, down, west, east);

                // Alpha untouched: sharpening the alpha channel produces halos around cut-outs.
                dst[dstRow + x] = (centre & 0xFF00_0000) | (r << 16) | (g << 8) | b;
            }
        }
    }

    /** Channel values are passed pre-shifted; the mask happens here to keep call sites short. */
    private int convolve(int centre, int up, int down, int west, int east) {
        int acc = centreWeight * (centre & 0xFF)
                + neighbourWeight * ((up & 0xFF) + (down & 0xFF) + (west & 0xFF) + (east & 0xFF));
        return Pixels.clamp8(acc >> FRACTION_BITS);
    }

    public double amount() {
        return amount;
    }

    @Override
    public String name() {
        return "sharpen(" + amount + ")";
    }
}
