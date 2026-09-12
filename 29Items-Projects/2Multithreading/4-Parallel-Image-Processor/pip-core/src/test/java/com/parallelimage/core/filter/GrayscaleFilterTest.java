package com.parallelimage.core.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.parallelimage.core.model.Tile;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GrayscaleFilterTest {

    @Test
    @DisplayName("BT.709 luma formula matches the exact fixed-point arithmetic for a mixed-channel pixel")
    void exactLumaForMixedChannels() {
        assertEquals(0xFF_1F1F1F, grayscaleOf(0xFF_112233));
    }

    @Test
    @DisplayName("a pure red pixel uses only the red weight")
    void pureRedUsesOnlyRedWeight() {
        assertEquals(0xFF_363636, grayscaleOf(0xFF_FF0000));
    }

    @Test
    @DisplayName("a pure green pixel uses only the green weight")
    void pureGreenUsesOnlyGreenWeight() {
        assertEquals(0xFF_B6B6B6, grayscaleOf(0xFF_00FF00));
    }

    @Test
    @DisplayName("a pure blue pixel uses only the blue weight")
    void pureBlueUsesOnlyBlueWeight() {
        assertEquals(0xFF_121212, grayscaleOf(0xFF_0000FF));
    }

    @Test
    @DisplayName("equal channels round-trip exactly because the fixed-point weights sum to 2^16")
    void equalChannelsRoundTripExactly() {
        assertEquals(0xFF_808080, grayscaleOf(0xFF_808080));
    }

    @Test
    @DisplayName("alpha is copied verbatim, never combined with the colour channels")
    void alphaIsCopiedVerbatim() {
        assertEquals(0x80, Pixels.alpha(grayscaleOf(0x80_FFFFFF)));
        assertEquals(0x00, Pixels.alpha(grayscaleOf(0x00_FFFFFF)));
    }

    @Test
    @DisplayName("row indexing is multiplicative: a later row is not confused with row 0")
    void laterRowUsesItsOwnStrideMultiple() {
        BufferedImage source = new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB);
        int[] in = Pixels.data(source);
        Arrays.fill(in, 0xFF_FF0000);
        // Row 2 is the only pure-blue row. A "y / srcStride" mutant collapses row 2 into row 0
        // (2 / 3 == 0) and would read red instead of blue for every pixel on this row.
        in[2 * 3] = 0xFF_0000FF;
        in[2 * 3 + 1] = 0xFF_0000FF;
        in[2 * 3 + 2] = 0xFF_0000FF;

        BufferedImage target = Pixels.sameShape(source);
        new GrayscaleFilter().apply(source, target, Tile.whole(3, 3));
        int[] out = Pixels.data(target);

        assertEquals(0xFF_121212, out[2 * 3], "row 2 must read its own row, not row 0");
        assertEquals(0xFF_121212, out[2 * 3 + 1]);
        assertEquals(0xFF_121212, out[2 * 3 + 2]);
        // The dst-row multiplication must also land on row 2, not overwrite row 0.
        assertEquals(0xFF_363636, out[0], "row 0 must be unaffected by row 2's write");
    }

    @Test
    @DisplayName("only pixels inside the tile are written; everything outside is left untouched")
    void onlyTileInteriorIsWritten() {
        BufferedImage source = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        Arrays.fill(Pixels.data(source), 0xFF_808080);
        BufferedImage target = Pixels.sameShape(source);
        int[] out = Pixels.data(target);
        Arrays.fill(out, 0x0BADF00D);

        Tile tile = new Tile(1, 1, 2, 2);
        new GrayscaleFilter().apply(source, target, tile);

        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                boolean inside = x >= 1 && x < 3 && y >= 1 && y < 3;
                int value = out[y * 4 + x];
                if (inside) {
                    assertEquals(0xFF_808080, value, "pixel " + x + "," + y + " inside the tile must be grayscaled");
                } else {
                    assertEquals(0x0BADF00D, value, "pixel " + x + "," + y + " outside the tile must be untouched");
                }
            }
        }
    }

    @Test
    @DisplayName("name() identifies the kernel")
    void nameIdentifiesKernel() {
        assertEquals("grayscale", new GrayscaleFilter().name());
    }

    private static int grayscaleOf(int argb) {
        BufferedImage source = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Pixels.data(source)[0] = argb;
        BufferedImage target = Pixels.sameShape(source);
        new GrayscaleFilter().apply(source, target, Tile.whole(1, 1));
        return Pixels.data(target)[0];
    }
}
