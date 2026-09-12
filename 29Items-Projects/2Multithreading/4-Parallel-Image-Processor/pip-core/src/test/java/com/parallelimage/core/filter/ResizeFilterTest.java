package com.parallelimage.core.filter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.parallelimage.core.pipeline.ImageOperation;
import java.awt.image.BufferedImage;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResizeFilterTest {

    @Test
    @DisplayName("non-aspect target ignores source dimensions entirely")
    void targetSizeNonAspectReturnsExactTarget() {
        int[] size = ResizeFilter.targetSize(500, 300, new ImageOperation.Resize(200, 150, false));

        assertArrayEquals(new int[] {200, 150}, size);
    }

    @Test
    @DisplayName("aspect-preserving target: width is the limiting dimension")
    void targetSizeAspectWidthBound() {
        int[] size = ResizeFilter.targetSize(400, 200, new ImageOperation.Resize(100, 100, true));

        assertArrayEquals(new int[] {100, 50}, size);
    }

    @Test
    @DisplayName("aspect-preserving target: height is the limiting dimension")
    void targetSizeAspectHeightBound() {
        int[] size = ResizeFilter.targetSize(200, 400, new ImageOperation.Resize(100, 100, true));

        assertArrayEquals(new int[] {50, 100}, size);
    }

    @Test
    @DisplayName("aspect-preserving target never floors a dimension to zero")
    void targetSizeFlooredAtOnePixel() {
        int[] size = ResizeFilter.targetSize(2000, 10, new ImageOperation.Resize(100, 100, true));

        assertArrayEquals(new int[] {100, 1}, size);
    }

    @Test
    @DisplayName("apply returns the same instance when the target already matches the source")
    void applySameDimensionsReturnsSourceUnchanged() {
        BufferedImage source = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);

        BufferedImage result = ResizeFilter.apply(source, new ImageOperation.Resize(50, 50, false));

        assertSame(source, result);
    }

    @Test
    @DisplayName("apply returns a new instance with the requested dimensions when they differ")
    void applyDifferentDimensionsReturnsNewInstance() {
        BufferedImage source = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);

        BufferedImage result = ResizeFilter.apply(source, new ImageOperation.Resize(20, 20, false));

        assertNotSame(source, result);
        assertEquals(20, result.getWidth());
        assertEquals(20, result.getHeight());
    }

    @Test
    @DisplayName("a target within 2x of the source skips the halving loop entirely")
    void applySmallDownscaleSkipsHalvingLoop() {
        BufferedImage source = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);

        BufferedImage result = ResizeFilter.apply(source, new ImageOperation.Resize(6, 6, false));

        assertEquals(6, result.getWidth());
        assertEquals(6, result.getHeight());
    }

    @Test
    @DisplayName("a 1x1 source can be upscaled")
    void applyUpscalesFromOnePixel() {
        BufferedImage source = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Pixels.data(source)[0] = 0xFF123456;

        BufferedImage result = ResizeFilter.apply(source, new ImageOperation.Resize(5, 5, false));

        assertEquals(5, result.getWidth());
        assertEquals(5, result.getHeight());
    }

    @Test
    @DisplayName("a 1x1 source resized to 1x1 is returned unchanged")
    void applyOnePixelToOnePixelReturnsSameInstance() {
        BufferedImage source = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Pixels.data(source)[0] = 0xFF123456;

        BufferedImage result = ResizeFilter.apply(source, new ImageOperation.Resize(1, 1, false));

        assertSame(source, result);
    }

    @Test
    @DisplayName("halving loop stops at the exact 2x boundary, matching a hand-traced trajectory")
    void applyMultiHalvingStopsAtExactBoundary() {
        BufferedImage source = new BufferedImage(64, 32, BufferedImage.TYPE_INT_RGB);
        int[] pixels = Pixels.data(source);
        Random random = new Random(20260819L);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = 0xFF00_0000 | random.nextInt(0x0100_0000);
        }

        BufferedImage result = ResizeFilter.apply(source, new ImageOperation.Resize(8, 4, false));

        assertEquals(8, result.getWidth());
        assertEquals(4, result.getHeight());
        int[] expected = {
            0x788b85, 0x887779, 0x71717f, 0x75797e, 0x88806d, 0x7c7f81, 0x7d7a85, 0x7e7f6d,
            0x7f7974, 0x829484, 0x848187, 0x6a877c, 0x7f8089, 0x757a7a, 0x868488, 0x867e85,
            0x7e8774, 0x838289, 0x8f7f8f, 0x848080, 0x77777d, 0x847f75, 0x797684, 0x7f888b,
            0x777f80, 0x747e7c, 0x887b7f, 0x7d8a86, 0x907f7d, 0x858476, 0x8c7185, 0x898b7f,
        };
        assertArrayEquals(expected, Pixels.data(result),
                "any change to the halving loop's boundary conditions changes which intermediate "
                        + "sizes are averaged through, and therefore the final pixel values");
    }

    @Test
    @DisplayName("a very narrow image stops halving once width would drop to 1, not before")
    void applyWideImageWidthGuardStopsHalving() {
        BufferedImage source = new BufferedImage(2, 10, BufferedImage.TYPE_INT_RGB);
        int[] pixels = Pixels.data(source);
        Random random = new Random(777001L);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = 0xFF00_0000 | random.nextInt(0x0100_0000);
        }

        BufferedImage result = ResizeFilter.apply(source, new ImageOperation.Resize(1, 1, false));

        assertEquals(1, result.getWidth());
        assertEquals(1, result.getHeight());
        assertEquals(0xac97b2, Pixels.data(result)[0],
                "the width > 2 guard must stop the loop before an extra halving step changes the "
                        + "averaging trajectory");
    }

    @Test
    @DisplayName("a very short image stops halving once height would drop to 1, not before")
    void applyTallImageHeightGuardStopsHalving() {
        BufferedImage source = new BufferedImage(10, 2, BufferedImage.TYPE_INT_RGB);
        int[] pixels = Pixels.data(source);
        Random random = new Random(777002L);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = 0xFF00_0000 | random.nextInt(0x0100_0000);
        }

        BufferedImage result = ResizeFilter.apply(source, new ImageOperation.Resize(1, 1, false));

        assertEquals(1, result.getWidth());
        assertEquals(1, result.getHeight());
        assertEquals(0xa85599, Pixels.data(result)[0],
                "the height > 2 guard must stop the loop before an extra halving step changes the "
                        + "averaging trajectory");
    }
}
