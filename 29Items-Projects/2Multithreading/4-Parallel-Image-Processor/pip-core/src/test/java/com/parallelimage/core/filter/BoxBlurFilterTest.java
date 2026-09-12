package com.parallelimage.core.filter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.core.fork.CancellationToken;
import com.parallelimage.core.model.Tile;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BoxBlurFilterTest {

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("radius() returns the value passed to the constructor")
        void radiusReturnsConstructorValue() {
            assertEquals(5, new BoxBlurFilter(5).radius());
        }

        @Test
        @DisplayName("name() includes the radius")
        void nameIncludesRadius() {
            assertEquals("blur(r=3)", new BoxBlurFilter(3).name());
        }

        @Test
        @DisplayName("a non-positive radius is rejected at construction")
        void nonPositiveRadiusRejected() {
            assertThrows(IllegalArgumentException.class, () -> new BoxBlurFilter(0));
            assertThrows(IllegalArgumentException.class, () -> new BoxBlurFilter(-1));
        }

        @Test
        @DisplayName("a null token is treated as CancellationToken.NONE: apply() never throws for it")
        void nullTokenNeverCancels() {
            BufferedImage source = noise(6, 6);
            BufferedImage target = Pixels.sameShape(source);
            assertDoesNotThrow(
                    () -> new BoxBlurFilter(1, null).apply(source, target, Tile.whole(6, 6)));
        }

        @Test
        @DisplayName("a token already cancelled before apply() causes CancellationException")
        void alreadyCancelledTokenThrowsDuringApply() {
            CancellationToken token = new CancellationToken();
            token.cancel();
            BufferedImage source = noise(6, 6);
            BufferedImage target = Pixels.sameShape(source);
            assertThrows(CancellationException.class,
                    () -> new BoxBlurFilter(1, token).apply(source, target, Tile.whole(6, 6)));
        }
    }

    @Nested
    @DisplayName("apply")
    class Apply {

        /**
         * Every configuration is compared, pixel for pixel, against {@link #expectedBlur}, a
         * straightforward re-implementation of the exact same two-pass separable algorithm (not a
         * naive single-pass 2D average, which would round differently). The {@code (0,0,4,6,1)}
         * case is chosen so that {@code tw * bandRows == 4 * 8 == 32}, an exact power of two: the
         * scratch band borrowed from {@link TileBufferPool} for that size has no slack, so an
         * off-by-one on the horizontal pass's outer loop bound overruns the array on the last band
         * row and fails the test via an {@code ArrayIndexOutOfBoundsException} rather than a value
         * mismatch — either way the mutant is caught.
         */
        @Test
        @DisplayName("matches a straightforward re-implementation of the same two-pass algorithm exactly")
        void matchesReferenceImplementationExactly() {
            BufferedImage source = noise(20, 20);
            int[][] configs = {
                {0, 0, 5, 5, 1},
                {2, 3, 6, 4, 2},
                {0, 0, 4, 6, 1},
                {3, 1, 7, 5, 3},
                {0, 0, 1, 1, 1},
                {19, 19, 1, 1, 4},
            };
            for (int[] c : configs) {
                Tile tile = new Tile(c[0], c[1], c[2], c[3]);
                int radius = c[4];
                BufferedImage target = Pixels.sameShape(source);
                new BoxBlurFilter(radius).apply(source, target, tile);

                int[] expected = expectedBlur(source, radius, tile);
                int[] actual = Pixels.data(target);
                int stride = Pixels.stride(target);
                int offset = Pixels.offset(target);
                for (int j = 0; j < tile.height(); j++) {
                    for (int i = 0; i < tile.width(); i++) {
                        int value = actual[offset + (tile.y() + j) * stride + (tile.x() + i)];
                        assertEquals(expected[j * tile.width() + i], value,
                                "mismatch at tile-local (" + i + "," + j + ") for tile " + tile
                                        + " radius " + radius);
                    }
                }
            }
        }

        @Test
        @DisplayName("a flat region stays exactly flat, including at the image edge (halo clamping)")
        void flatRegionStaysFlat() {
            BufferedImage source = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            java.util.Arrays.fill(Pixels.data(source), 0xFF_7F7F7F);
            BufferedImage target = Pixels.sameShape(source);
            new BoxBlurFilter(3).apply(source, target, Tile.whole(10, 10));

            for (int value : Pixels.data(target)) {
                assertEquals(0xFF_7F7F7F, value);
            }
        }

        @Test
        @DisplayName("alpha is blurred through the same averaging window as the colour channels")
        void alphaIsBlurredLikeColour() {
            BufferedImage source = new BufferedImage(5, 1, BufferedImage.TYPE_INT_ARGB);
            int[] in = Pixels.data(source);
            in[0] = 0x00_000000;
            in[1] = 0x00_000000;
            in[2] = 0xFF_000000;
            in[3] = 0x00_000000;
            in[4] = 0x00_000000;

            BufferedImage target = Pixels.sameShape(source);
            new BoxBlurFilter(1).apply(source, target, Tile.whole(5, 1));

            // Window size 3 at x=2: (0 + 255 + 0) / 3 == 85.
            assertEquals(85, Pixels.alpha(Pixels.data(target)[2]));
        }

        @Test
        @DisplayName("borrowed scratch buffers are always returned to the pool, even via the finally block")
        void scratchBuffersAreAlwaysReleased() {
            int tw = 4;
            int radius = 1;
            int th = 2;
            int bandLength = tw * (th + 2 * radius); // 16
            int accLength = tw; // 4

            drainBucket(bandLength);
            drainBucket(accLength);

            int[] bandSentinel = new int[bandLength];
            int[] accSentinel1 = new int[accLength];
            int[] accSentinel2 = new int[accLength];
            int[] accSentinel3 = new int[accLength];
            int[] accSentinel4 = new int[accLength];
            TileBufferPool.release(bandSentinel);
            TileBufferPool.release(accSentinel1);
            TileBufferPool.release(accSentinel2);
            TileBufferPool.release(accSentinel3);
            TileBufferPool.release(accSentinel4);

            BufferedImage source = noise(10, 10);
            BufferedImage target = Pixels.sameShape(source);
            new BoxBlurFilter(radius).apply(source, target, new Tile(0, 0, tw, th));

            assertSame(bandSentinel, TileBufferPool.borrow(bandLength),
                    "the band scratch buffer must be released back to the pool");

            List<int[]> accAfter = List.of(
                    TileBufferPool.borrow(accLength), TileBufferPool.borrow(accLength),
                    TileBufferPool.borrow(accLength), TileBufferPool.borrow(accLength));
            assertTrue(containsByIdentity(accAfter, accSentinel1),
                    "accA's sentinel never came back to the pool: its release() call was skipped");
            assertTrue(containsByIdentity(accAfter, accSentinel2),
                    "accR's sentinel never came back to the pool: its release() call was skipped");
            assertTrue(containsByIdentity(accAfter, accSentinel3),
                    "accG's sentinel never came back to the pool: its release() call was skipped");
            assertTrue(containsByIdentity(accAfter, accSentinel4),
                    "accB's sentinel never came back to the pool: its release() call was skipped");
        }
    }

    /** Drains any arrays already pooled for this bucket so a test starts from a known-empty pool. */
    private static void drainBucket(int minLength) {
        for (int i = 0; i < 8; i++) {
            TileBufferPool.borrow(minLength);
        }
    }

    private static boolean containsByIdentity(List<int[]> haystack, int[] needle) {
        for (int[] candidate : haystack) {
            if (candidate == needle) {
                return true;
            }
        }
        return false;
    }

    /** Deterministic pseudo-random content: a fixed seed keeps failures reproducible. */
    private static BufferedImage noise(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = Pixels.data(image);
        Random random = new Random(20260819L);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = random.nextInt();
        }
        return image;
    }

    /**
     * Re-implements {@link BoxBlurFilter}'s exact two-pass separable algorithm with plain nested
     * loops instead of a sliding window, recomputing each window sum from scratch. This costs
     * O(radius) per pixel instead of O(1), but that is exactly the point: it is a second, structurally
     * independent implementation of the same arithmetic (including the intermediate rounding when the
     * horizontal pass's averages are packed back into the scratch band) that mutation testing cannot
     * accidentally satisfy by re-deriving the same bug twice.
     */
    private static int[] expectedBlur(BufferedImage source, int radius, Tile tile) {
        int[] src = Pixels.data(source);
        int srcStride = Pixels.stride(source);
        int srcOffset = Pixels.offset(source);
        int srcWidth = source.getWidth();
        int srcHeight = source.getHeight();
        int window = 2 * radius + 1;
        int tw = tile.width();
        int th = tile.height();
        int bandRows = th + 2 * radius;

        int[] band = new int[tw * bandRows];
        for (int j = 0; j < bandRows; j++) {
            int sy = Pixels.clampCoord(tile.y() - radius + j, srcHeight);
            int srcRow = srcOffset + sy * srcStride;
            for (int i = 0; i < tw; i++) {
                int sa = 0;
                int sr = 0;
                int sg = 0;
                int sb = 0;
                for (int dx = -radius; dx <= radius; dx++) {
                    int argb = src[srcRow + Pixels.clampCoord(tile.x() + i + dx, srcWidth)];
                    sa += Pixels.alpha(argb);
                    sr += Pixels.red(argb);
                    sg += Pixels.green(argb);
                    sb += Pixels.blue(argb);
                }
                band[j * tw + i] = Pixels.pack(sa / window, sr / window, sg / window, sb / window);
            }
        }

        int[] result = new int[tw * th];
        for (int j = 0; j < th; j++) {
            for (int i = 0; i < tw; i++) {
                int sa = 0;
                int sr = 0;
                int sg = 0;
                int sb = 0;
                for (int k = 0; k < window; k++) {
                    int argb = band[(j + k) * tw + i];
                    sa += Pixels.alpha(argb);
                    sr += Pixels.red(argb);
                    sg += Pixels.green(argb);
                    sb += Pixels.blue(argb);
                }
                result[j * tw + i] = Pixels.pack(sa / window, sr / window, sg / window, sb / window);
            }
        }
        return result;
    }
}
