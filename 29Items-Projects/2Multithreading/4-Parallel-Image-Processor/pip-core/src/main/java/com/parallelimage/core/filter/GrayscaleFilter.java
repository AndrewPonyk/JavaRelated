package com.parallelimage.core.filter;

import com.parallelimage.core.model.Tile;
import com.parallelimage.core.pipeline.TileKernel;
import java.awt.image.BufferedImage;

/**
 * Luma conversion using the ITU-R BT.709 coefficients (0.2126 R, 0.7152 G, 0.0722 B).
 *
 * <p>The textbook alternative — averaging the three channels — is wrong for the same reason it looks
 * wrong: the human eye is roughly six times more sensitive to green than to blue, so a flat average
 * turns blue skies muddy grey and green foliage far too dark.
 *
 * <p><strong>Pixel-local</strong>, so this is the ideal tileable kernel: no halo, no neighbour reads,
 * perfect cache behaviour, and identical output at any parallelism.
 *
 * <p>Stateless and therefore safely shared by every worker; a single instance serves the whole batch.
 */
public final class GrayscaleFilter implements TileKernel {

    /** Fixed-point weights scaled by 2^16 — integer math avoids a float round-trip per pixel. */
    private static final int W_RED = 13_933;    // 0.2126 * 65536
    private static final int W_GREEN = 46_871;  // 0.7152 * 65536
    private static final int W_BLUE = 4_732;    // 0.0722 * 65536
    private static final int SHIFT = 16;

    @Override
    public void apply(BufferedImage source, BufferedImage target, Tile tile) {
        int[] src = Pixels.data(source);
        int[] dst = Pixels.data(target);
        int srcStride = Pixels.stride(source);
        int dstStride = Pixels.stride(target);
        int srcOffset = Pixels.offset(source);
        int dstOffset = Pixels.offset(target);

        int maxY = tile.maxY();
        int maxX = tile.maxX();
        for (int y = tile.y(); y < maxY; y++) {
            int srcRow = srcOffset + y * srcStride;
            int dstRow = dstOffset + y * dstStride;
            for (int x = tile.x(); x < maxX; x++) {
                int argb = src[srcRow + x];
                int luma = (W_RED * ((argb >> 16) & 0xFF)
                        + W_GREEN * ((argb >> 8) & 0xFF)
                        + W_BLUE * (argb & 0xFF)) >>> SHIFT;
                // Alpha is copied verbatim: greyscaling must not make a transparent PNG opaque.
                dst[dstRow + x] = (argb & 0xFF00_0000) | (luma << 16) | (luma << 8) | luma;
            }
        }
    }

    @Override
    public String name() {
        return "grayscale";
    }
}
