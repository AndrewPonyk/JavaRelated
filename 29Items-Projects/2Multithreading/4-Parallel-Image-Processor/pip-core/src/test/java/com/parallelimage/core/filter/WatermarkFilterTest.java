package com.parallelimage.core.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.parallelimage.core.pipeline.ImageOperation;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WatermarkFilterTest {

    /** {@link ImageOperation.Watermark} only checks this for {@code null}, never opens it. */
    private static final Path DUMMY_OVERLAY_PATH = Path.of("dummy.png");

    private static final int BACKGROUND = 0xFFAAAAAA;

    @Test
    @DisplayName("TOP_LEFT anchors the mark at exactly (margin, margin)")
    void topLeftAnchorPlacesMarkAtMargin() {
        BufferedImage image = flatImage(100, 50, BACKGROUND);
        WatermarkFilter.apply(image, watermark(ImageOperation.Anchor.TOP_LEFT, 1.0d, 5), redOverlay(40, 20));
        int[] pixels = Pixels.data(image);

        assertEquals(0xFFFF0000, pixels[5 * 100 + 5], "top-left corner of the mark");
        assertEquals(BACKGROUND, pixels[4 * 100 + 4], "just outside the mark, above and left of margin");
        assertEquals(0xFFFF0000, pixels[12 * 100 + 20], "bottom-right corner of the drawn mark");
        assertEquals(BACKGROUND, pixels[13 * 100 + 21], "just past the bottom-right corner of the mark");
    }

    @Test
    @DisplayName("TOP_RIGHT anchors the mark's right edge at width - margin")
    void topRightAnchorPlacesMarkAtRightEdge() {
        BufferedImage image = flatImage(100, 50, BACKGROUND);
        WatermarkFilter.apply(image, watermark(ImageOperation.Anchor.TOP_RIGHT, 1.0d, 5), redOverlay(40, 20));
        int[] pixels = Pixels.data(image);

        assertEquals(0xFFFF0000, pixels[5 * 100 + 79]);
        assertEquals(BACKGROUND, pixels[4 * 100 + 78]);
    }

    @Test
    @DisplayName("BOTTOM_LEFT anchors the mark's bottom edge at height - margin")
    void bottomLeftAnchorPlacesMarkAtBottomEdge() {
        BufferedImage image = flatImage(100, 50, BACKGROUND);
        WatermarkFilter.apply(image, watermark(ImageOperation.Anchor.BOTTOM_LEFT, 1.0d, 5), redOverlay(40, 20));
        int[] pixels = Pixels.data(image);

        assertEquals(0xFFFF0000, pixels[37 * 100 + 5]);
        assertEquals(BACKGROUND, pixels[36 * 100 + 4]);
    }

    @Test
    @DisplayName("BOTTOM_RIGHT anchors both edges at (width, height) minus margin")
    void bottomRightAnchorPlacesMarkAtOppositeCorner() {
        BufferedImage image = flatImage(100, 50, BACKGROUND);
        WatermarkFilter.apply(image, watermark(ImageOperation.Anchor.BOTTOM_RIGHT, 1.0d, 5), redOverlay(40, 20));
        int[] pixels = Pixels.data(image);

        assertEquals(0xFFFF0000, pixels[37 * 100 + 79], "top-left corner of the drawn mark");
        assertEquals(0xFFFF0000, pixels[44 * 100 + 94], "bottom-right corner of the drawn mark");
        assertEquals(BACKGROUND, pixels[45 * 100 + 95], "just past the drawn mark");
        assertEquals(BACKGROUND, pixels[36 * 100 + 78], "just before the drawn mark");
    }

    @Test
    @DisplayName("CENTER divides the leftover space evenly on both axes")
    void centerAnchorDividesLeftoverSpaceEvenly() {
        BufferedImage image = flatImage(100, 50, BACKGROUND);
        WatermarkFilter.apply(image, watermark(ImageOperation.Anchor.CENTER, 1.0d, 5), redOverlay(40, 20));
        int[] pixels = Pixels.data(image);

        assertEquals(0xFFFF0000, pixels[21 * 100 + 42], "top-left corner of the centred mark");
        assertEquals(BACKGROUND, pixels[20 * 100 + 41]);
        assertEquals(0xFFFF0000, pixels[28 * 100 + 57], "bottom-right corner of the centred mark");
        assertEquals(BACKGROUND, pixels[29 * 100 + 58]);
    }

    @Test
    @DisplayName("a margin too large for the image clamps the anchor and clips the mark off-canvas")
    void oversizedMarginClampsAnchorAndClipsMarkOffCanvas() {
        BufferedImage image = flatImage(100, 50, BACKGROUND);
        WatermarkFilter.apply(image, watermark(ImageOperation.Anchor.BOTTOM_RIGHT, 1.0d, 90), redOverlay(40, 20));
        int[] pixels = Pixels.data(image);

        for (int value : pixels) {
            assertEquals(BACKGROUND, value, "margin (90) exceeds the space available in a 100x50 "
                    + "image, so Math.max must fall back to the margin itself and the mark must land "
                    + "entirely off-canvas, leaving every pixel untouched");
        }
    }

    @Test
    @DisplayName("opacity 0.0 leaves the background completely unchanged")
    void opacityZeroLeavesBackgroundUnchanged() {
        BufferedImage image = flatImage(20, 10, 0xFFC8C8C8);
        WatermarkFilter.apply(image, watermark(ImageOperation.Anchor.TOP_LEFT, 0.0d, 0), blackOverlay(20, 10));

        assertEquals(0xFFC8C8C8, Pixels.data(image)[0]);
    }

    @Test
    @DisplayName("opacity 1.0 fully replaces the background with the overlay colour")
    void opacityOneFullyReplacesBackground() {
        BufferedImage image = flatImage(20, 10, 0xFFC8C8C8);
        WatermarkFilter.apply(image, watermark(ImageOperation.Anchor.TOP_LEFT, 1.0d, 0), blackOverlay(20, 10));

        assertEquals(0xFF000000, Pixels.data(image)[0]);
    }

    @Test
    @DisplayName("a fractional opacity blends source and overlay by the exact SRC_OVER formula")
    void fractionalOpacityBlendsByExactFormula() {
        BufferedImage image = flatImage(20, 10, 0xFFC8C8C8);
        WatermarkFilter.apply(image, watermark(ImageOperation.Anchor.TOP_LEFT, 0.4d, 0), blackOverlay(20, 10));
        int blended = Pixels.data(image)[0];

        // Cr = Cs*As + Cd*(1-As) = 0*0.4 + 200*0.6 = 120, for both opaque source and destination.
        assertEquals(255, Pixels.alpha(blended));
        assertEquals(120, Pixels.red(blended));
        assertEquals(120, Pixels.green(blended));
        assertEquals(120, Pixels.blue(blended));
    }

    @Test
    @DisplayName("apply returns the same image instance it mutated, for call-site chaining")
    void applyReturnsSameInstanceForChaining() {
        BufferedImage image = flatImage(20, 10, BACKGROUND);

        BufferedImage result = WatermarkFilter.apply(image, watermark(ImageOperation.Anchor.TOP_LEFT, 1.0d, 0),
                blackOverlay(20, 10));

        assertSame(image, result);
    }

    private static ImageOperation.Watermark watermark(ImageOperation.Anchor anchor, double opacity, int marginPx) {
        return new ImageOperation.Watermark(null, DUMMY_OVERLAY_PATH, anchor, opacity, marginPx);
    }

    private static BufferedImage flatImage(int width, int height, int argb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(Pixels.data(image), argb);
        return image;
    }

    private static BufferedImage redOverlay(int width, int height) {
        return flatImage(width, height, 0xFFFF0000);
    }

    private static BufferedImage blackOverlay(int width, int height) {
        return flatImage(width, height, 0xFF000000);
    }
}
