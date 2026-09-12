package com.parallelimage.core.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.core.pipeline.ImageOperation.EnhanceMode;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link PassthroughEnhancer} tests.
 *
 * <p>The contrast-stretch case uses a 100x1 grayscale gradient (pixel value {@code v} at column
 * {@code v}, for {@code v} in {@code 0..99}) specifically because its luma histogram has exactly one
 * pixel per bin. That makes the 0.5% clip ({@code max(1, total/200)}) clip exactly one pixel from
 * each end, so {@code low=0} and {@code high=99} fall out exactly rather than approximately — and the
 * BT.709-style integer luma weights (299+587+114)/1000 sum to exactly 1000, so a gray pixel's luma
 * equals its channel value with no rounding error. That makes every expected output pixel computable
 * by hand instead of asserted against a tolerance.
 */
class PassthroughEnhancerTest {

    private final PassthroughEnhancer enhancer = new PassthroughEnhancer();

    private static BufferedImage grayGradient(int width) {
        BufferedImage image = new BufferedImage(width, 1, BufferedImage.TYPE_INT_ARGB);
        for (int v = 0; v < width; v++) {
            int argb = 0xFF00_0000 | (v << 16) | (v << 8) | v;
            image.setRGB(v, 0, argb);
        }
        return image;
    }

    @Nested
    @DisplayName("enhance")
    class Enhance {

        @Test
        @DisplayName("a non-CLAHE mode passes the source through unchanged, by reference")
        void nonClaheModePassesThrough() {
            BufferedImage source = grayGradient(4);
            assertSame(source, enhancer.enhance(source, EnhanceMode.DENOISE, 1.0));
            assertSame(source, enhancer.enhance(source, EnhanceMode.SUPER_RESOLUTION, 1.0));
        }

        @Test
        @DisplayName("zero or negative strength passes the source through unchanged, by reference")
        void nonPositiveStrengthPassesThrough() {
            BufferedImage source = grayGradient(4);
            assertSame(source, enhancer.enhance(source, EnhanceMode.CLAHE, 0.0));
            assertSame(source, enhancer.enhance(source, EnhanceMode.CLAHE, -1.0));
        }

        @Test
        @DisplayName("a flat image (high-low < 8) is returned unchanged, by reference")
        void flatImagePassesThrough() {
            BufferedImage source = grayGradient(1);
            // A single-pixel image has low == high, so high - low == 0 < 8.
            assertSame(source, enhancer.enhance(source, EnhanceMode.CLAHE, 1.0));
        }

        @Test
        @DisplayName("full strength stretches the darkest and lightest columns to exactly black and white")
        void fullStrengthStretchesToExtremes() {
            BufferedImage source = grayGradient(100);

            BufferedImage result = enhancer.enhance(source, EnhanceMode.CLAHE, 1.0);

            assertEquals(0xFF00_0000, result.getRGB(0, 0), "darkest column must clamp to black");
            assertEquals(0xFFFF_FFFF, result.getRGB(99, 0), "lightest column must clamp to white");
        }

        @Test
        @DisplayName("half strength blends the stretched value halfway back toward the original")
        void halfStrengthBlendsTowardOriginal() {
            BufferedImage source = grayGradient(100);

            BufferedImage result = enhancer.enhance(source, EnhanceMode.CLAHE, 0.5);

            // v=99: stretched=255, lut = 99 + (255-99)*0.5 = 177 exactly.
            int expected = 0xFF00_0000 | (177 << 16) | (177 << 8) | 177;
            assertEquals(expected, result.getRGB(99, 0));
            // v=0: stretched=0 regardless of strength, since (v-low)=0.
            assertEquals(0xFF00_0000, result.getRGB(0, 0));
        }

        @Test
        @DisplayName("alpha is preserved through the stretch")
        void alphaIsPreserved() {
            BufferedImage source = new BufferedImage(100, 1, BufferedImage.TYPE_INT_ARGB);
            for (int v = 0; v < 100; v++) {
                int argb = (0x80 << 24) | (v << 16) | (v << 8) | v;
                source.setRGB(v, 0, argb);
            }

            BufferedImage result = enhancer.enhance(source, EnhanceMode.CLAHE, 1.0);

            assertEquals(0x80, (result.getRGB(50, 0) >>> 24) & 0xFF);
        }
    }

    @Nested
    @DisplayName("provider metadata")
    class Metadata {

        @Test
        @DisplayName("is always available")
        void alwaysAvailable() {
            assertTrue(enhancer.isAvailable());
        }

        @Test
        @DisplayName("supports only CLAHE")
        void supportsOnlyClahe() {
            assertTrue(enhancer.supports(EnhanceMode.CLAHE));
            assertEquals(false, enhancer.supports(EnhanceMode.DENOISE));
            assertEquals(false, enhancer.supports(EnhanceMode.SUPER_RESOLUTION));
        }

        @Test
        @DisplayName("has the lowest possible priority so a real provider always outranks it")
        void lowestPriority() {
            assertEquals(Integer.MIN_VALUE, enhancer.priority());
        }

        @Test
        @DisplayName("describes itself as the pure-Java fallback")
        void describesItself() {
            assertEquals("passthrough (pure Java, no OpenCV)", enhancer.describe());
        }
    }
}
