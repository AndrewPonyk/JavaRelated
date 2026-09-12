package com.parallelimage.core.filter;

import com.parallelimage.core.pipeline.ImageOperation;
import com.parallelimage.core.util.Preconditions;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Whole-image bilinear rescale.
 *
 * <h2>Why this one is not a {@link com.parallelimage.core.pipeline.TileKernel}</h2>
 * Every other filter maps pixel {@code (x, y)} of the source onto pixel {@code (x, y)} of a
 * same-sized destination, which is what makes "write only inside your tile" a coherent rule. A
 * resample breaks that: destination {@code (x, y)} corresponds to a fractional source region, so a
 * tiled version would need per-tile coordinate mapping plus an overlapping source read, and would
 * still be prone to seams from independent rounding at the boundaries. Resize therefore runs whole-
 * image on the worker that owns the job, and parallelism comes from Level 1 (other images) instead.
 *
 * <h2>Progressive halving</h2>
 * A single bilinear step from 6000&nbsp;px to 400&nbsp;px samples 4 of every ~225 source pixels and
 * throws the rest away, which aliases badly — thumbnails come out speckled and hard-edged. Halving
 * repeatedly until within 2&times; of the target averages the discarded pixels in instead. It costs
 * about 33% more work than one step ({@code 1 + 1/4 + 1/16 + …}) for a large visible quality gain.
 *
 * <p>Stateless; all state lives in locals, so one instance is safe to share.
 */
public final class ResizeFilter {

    private ResizeFilter() {
        throw new AssertionError("no instances");
    }

    /**
     * Computes the output geometry, honouring {@code preserveAspectRatio}.
     *
     * <p>Never upscales past the requested box and never returns a zero dimension: an extremely wide
     * panorama fitted into a small box would round its height to 0 and {@code BufferedImage} would
     * reject it, so both edges are floored at 1.
     *
     * @return {@code {width, height}}
     */
    public static int[] targetSize(int sourceWidth, int sourceHeight, ImageOperation.Resize op) {
        Preconditions.requirePositive(sourceWidth, "sourceWidth");
        Preconditions.requirePositive(sourceHeight, "sourceHeight");
        if (!op.preserveAspectRatio()) {
            return new int[] {op.targetWidth(), op.targetHeight()};
        }
        double scale = Math.min(
                op.targetWidth() / (double) sourceWidth,
                op.targetHeight() / (double) sourceHeight);
        return new int[] {
            Math.max(1, (int) Math.round(sourceWidth * scale)),
            Math.max(1, (int) Math.round(sourceHeight * scale)),
        };
    }

    /**
     * Rescales {@code source}, returning a new image. {@code source} is not modified.
     *
     * <p>Returns the argument unchanged when the target geometry already matches, so a "resize to
     * 1920×1080" batch does not needlessly re-encode images that are already that size.
     */
    public static BufferedImage apply(BufferedImage source, ImageOperation.Resize op) {
        Preconditions.requireNonNull(source, "source");
        Preconditions.requireNonNull(op, "op");

        int[] target = targetSize(source.getWidth(), source.getHeight(), op);
        int targetWidth = target[0];
        int targetHeight = target[1];
        if (targetWidth == source.getWidth() && targetHeight == source.getHeight()) {
            return source;
        }

        BufferedImage current = source;
        // Halve while more than 2x away, then do the final (possibly non-integral) step.
        while (current.getWidth() / 2 >= targetWidth && current.getHeight() / 2 >= targetHeight
                && current.getWidth() > 2 && current.getHeight() > 2) {
            current = scaleOnce(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        if (current.getWidth() != targetWidth || current.getHeight() != targetHeight) {
            current = scaleOnce(current, targetWidth, targetHeight);
        }
        return current;
    }

    private static BufferedImage scaleOnce(BufferedImage source, int width, int height) {
        BufferedImage scaled = new BufferedImage(width, height,
                Pixels.isIntPacked(source) ? source.getType() : BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            // Graphics2D wraps native state. Not disposing leaks it until GC notices, which under a
            // concurrent collector can be a long time (TECH-NOTES §3.6 E6).
            g.dispose();
        }
        return scaled;
    }
}
