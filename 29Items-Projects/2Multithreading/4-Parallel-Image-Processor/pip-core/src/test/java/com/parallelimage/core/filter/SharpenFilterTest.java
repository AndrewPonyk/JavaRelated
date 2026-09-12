package com.parallelimage.core.filter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.parallelimage.core.model.Tile;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SharpenFilterTest {

    @Test
    @DisplayName("amount() and name() report exactly the constructor argument")
    void amountAndNameReportConstructorArgument() {
        SharpenFilter filter = new SharpenFilter(1.75d);

        assertEquals(1.75d, filter.amount());
        assertEquals("sharpen(1.75)", filter.name());
    }

    @Test
    @DisplayName("amount 0 is an exact identity, not an approximate one")
    void amountZeroIsExactIdentity() {
        BufferedImage source = new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = Pixels.data(source);
        pixels[0] = Pixels.pack(0xFF, 10, 20, 30);
        pixels[1] = Pixels.pack(0xFF, 200, 5, 90);
        pixels[2] = Pixels.pack(0x80, 40, 150, 60);
        pixels[3] = Pixels.pack(0xFF, 250, 0, 128);
        pixels[4] = Pixels.pack(0xFF, 128, 128, 128);
        pixels[5] = Pixels.pack(0xFF, 5, 250, 1);
        pixels[6] = Pixels.pack(0xFF, 60, 60, 200);
        pixels[7] = Pixels.pack(0xFF, 90, 90, 90);
        pixels[8] = Pixels.pack(0xFF, 30, 170, 220);

        BufferedImage target = Pixels.sameShape(source);
        new SharpenFilter(0.0d).apply(source, target, Tile.whole(3, 3));

        assertArrayEquals(pixels, Pixels.data(target));
    }

    @Test
    @DisplayName("a flat region is unaffected by sharpening, because the weights sum to one")
    void flatRegionIsUnaffectedByAnyAmount() {
        BufferedImage source = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        int[] pixels = Pixels.data(source);
        java.util.Arrays.fill(pixels, Pixels.pack(0xFF, 77, 133, 201));

        BufferedImage target = Pixels.sameShape(source);
        new SharpenFilter(1.0d).apply(source, target, Tile.whole(4, 4));

        assertArrayEquals(pixels, Pixels.data(target),
                "a uniform region has no gradient to amplify: centreWeight + 4*neighbourWeight == ONE");
    }

    @Test
    @DisplayName("a sharp edge is amplified by exactly the hand-derived fixed-point amount")
    void sharpEdgeAmplifiedByExactComputedAmount() {
        int background = Pixels.pack(0xFF, 100, 150, 50);
        int centreColour = Pixels.pack(0xFF, 140, 80, 130);
        BufferedImage source = new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = Pixels.data(source);
        java.util.Arrays.fill(pixels, background);
        pixels[1 * 3 + 1] = centreColour;

        BufferedImage target = Pixels.sameShape(source);
        new SharpenFilter(0.25d).apply(source, target, Tile.whole(3, 3));
        int[] out = Pixels.data(target);

        int centre = out[1 * 3 + 1];
        assertEquals(255, Pixels.alpha(centre));
        assertEquals(180, Pixels.red(centre));
        assertEquals(10, Pixels.green(centre));
        assertEquals(210, Pixels.blue(centre));

        int[] edgeAdjacentToCentre = {1 * 3 + 0, 0 * 3 + 1, 1 * 3 + 2, 2 * 3 + 1};
        for (int index : edgeAdjacentToCentre) {
            assertEquals(90, Pixels.red(out[index]), "index " + index);
            assertEquals(167, Pixels.green(out[index]), "index " + index);
            assertEquals(30, Pixels.blue(out[index]), "index " + index);
        }

        int[] corners = {0, 2, 6, 8};
        for (int index : corners) {
            assertEquals(background, out[index], "corner index " + index + " has no adjacency to the "
                    + "differing centre pixel and must stay exactly at the background colour");
        }
    }

    @Test
    @DisplayName("convolution result clamps to [0, 255] at both extremes")
    void convolutionClampsAtBothExtremes() {
        int background = Pixels.pack(0xFF, 10, 245, 128);
        int centreColour = Pixels.pack(0xFF, 250, 5, 128);
        BufferedImage source = new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = Pixels.data(source);
        java.util.Arrays.fill(pixels, background);
        pixels[1 * 3 + 1] = centreColour;

        BufferedImage target = Pixels.sameShape(source);
        new SharpenFilter(2.0d).apply(source, target, Tile.whole(3, 3));
        int centre = Pixels.data(target)[1 * 3 + 1];

        assertEquals(255, Pixels.red(centre), "amplifying a bright centre against a dark halo must "
                + "clamp at the high end, not wrap or overflow");
        assertEquals(0, Pixels.green(centre), "amplifying a dark centre against a bright halo must "
                + "clamp at the low end, not go negative");
        assertEquals(128, Pixels.blue(centre), "a channel with no gradient must pass through unclamped");
    }

    @Test
    @DisplayName("vertical edges clamp the halo to the nearest real row instead of wrapping")
    void verticalEdgesClampToNearestRow() {
        BufferedImage column = new BufferedImage(1, 3, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = Pixels.data(column);
        pixels[0] = Pixels.pack(0xFF, 120, 120, 120);
        pixels[1] = Pixels.pack(0xFF, 150, 150, 150);
        pixels[2] = Pixels.pack(0xFF, 90, 90, 90);

        BufferedImage target = Pixels.sameShape(column);
        new SharpenFilter(1.0d).apply(column, target, Tile.whole(1, 3));
        int[] out = Pixels.data(target);

        assertEquals(90, Pixels.red(out[0]), "top row must clamp its 'above' sample to itself");
        assertEquals(240, Pixels.red(out[1]));
        assertEquals(30, Pixels.red(out[2]), "bottom row must clamp its 'below' sample to itself");
    }

    @Test
    @DisplayName("horizontal edges clamp the halo to the nearest real column instead of wrapping")
    void horizontalEdgesClampToNearestColumn() {
        BufferedImage row = new BufferedImage(3, 1, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = Pixels.data(row);
        pixels[0] = Pixels.pack(0xFF, 120, 120, 120);
        pixels[1] = Pixels.pack(0xFF, 150, 150, 150);
        pixels[2] = Pixels.pack(0xFF, 90, 90, 90);

        BufferedImage target = Pixels.sameShape(row);
        new SharpenFilter(1.0d).apply(row, target, Tile.whole(3, 1));
        int[] out = Pixels.data(target);

        assertEquals(90, Pixels.red(out[0]), "left column must clamp its 'west' sample to itself");
        assertEquals(240, Pixels.red(out[1]));
        assertEquals(30, Pixels.red(out[2]), "right column must clamp its 'east' sample to itself");
    }

    @Test
    @DisplayName("sharpening never touches the alpha channel")
    void alphaChannelIsCopiedVerbatim() {
        BufferedImage source = new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = Pixels.data(source);
        pixels[0] = Pixels.pack(0x11, 10, 20, 30);
        pixels[1] = Pixels.pack(0x22, 200, 5, 90);
        pixels[2] = Pixels.pack(0x33, 40, 150, 60);
        pixels[3] = Pixels.pack(0x44, 250, 0, 128);
        pixels[4] = Pixels.pack(0x55, 128, 128, 128);
        pixels[5] = Pixels.pack(0x66, 5, 250, 1);
        pixels[6] = Pixels.pack(0x77, 60, 60, 200);
        pixels[7] = Pixels.pack(0x88, 90, 90, 90);
        pixels[8] = Pixels.pack(0x99, 30, 170, 220);

        BufferedImage target = Pixels.sameShape(source);
        new SharpenFilter(3.0d).apply(source, target, Tile.whole(3, 3));
        int[] out = Pixels.data(target);

        for (int i = 0; i < pixels.length; i++) {
            assertEquals(Pixels.alpha(pixels[i]), Pixels.alpha(out[i]), "index " + i);
        }
    }
}
