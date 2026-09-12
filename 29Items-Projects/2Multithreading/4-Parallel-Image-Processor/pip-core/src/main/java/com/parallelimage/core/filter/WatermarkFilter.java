package com.parallelimage.core.filter;

import com.parallelimage.core.pipeline.ImageOperation;
import com.parallelimage.core.util.Preconditions;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * Composites a text and/or image watermark onto a whole image.
 *
 * <h2>Why whole-image</h2>
 * {@link Graphics2D} is not thread-safe, and a watermark straddling a tile boundary would need two
 * workers to share one context. Splitting the mark itself (each tile draws its own clipped slice)
 * would work but produce visible seams in the antialiased glyph edges. Since a watermark touches a
 * few percent of the pixels, there is nothing to gain: this runs once, on the worker that owns the
 * job, after the tileable stages.
 *
 * <h2>Font sizing</h2>
 * A fixed point size looks correct on the developer's test image and absurd on everything else — 5%
 * of the shorter edge is used instead, so a 400&nbsp;px thumbnail and a 8000&nbsp;px panorama get
 * proportionally the same mark.
 *
 * <p>Mutates and returns the image it is given. Safe because the pipeline owns that buffer
 * exclusively for the duration of one job.
 */
public final class WatermarkFilter {

    /** Watermark height as a fraction of the image's shorter edge. */
    private static final double FONT_SCALE = 0.05d;

    /** Below this the text would be unreadable, so clamp. */
    private static final int MIN_FONT_PX = 10;

    private WatermarkFilter() {
        throw new AssertionError("no instances");
    }

    /**
     * Draws the watermark onto {@code image} in place.
     *
     * @param image   destination, modified in place
     * @param op      watermark parameters
     * @param overlay decoded overlay image, or {@code null} for text-only. Loading is the caller's
     *                job so this package stays free of {@code ImageIO} (and of its thread-safety
     *                caveats).
     * @return {@code image}, for call-site chaining
     */
    public static BufferedImage apply(BufferedImage image, ImageOperation.Watermark op,
            BufferedImage overlay) {
        Preconditions.requireNonNull(image, "image");
        Preconditions.requireNonNull(op, "op");

        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, (float) op.opacity()));

            if (overlay != null) {
                drawOverlay(g, image, op, overlay);
            }
            if (op.text() != null && !op.text().isBlank()) {
                drawText(g, image, op);
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    private static void drawOverlay(Graphics2D g, BufferedImage image,
            ImageOperation.Watermark op, BufferedImage overlay) {
        int shortEdge = Math.min(image.getWidth(), image.getHeight());
        int height = Math.max(1, (int) Math.round(shortEdge * FONT_SCALE * 3));
        int width = Math.max(1,
                (int) Math.round(overlay.getWidth() * (height / (double) overlay.getHeight())));
        int[] xy = anchor(image, op, width, height);
        g.drawImage(overlay, xy[0], xy[1], width, height, null);
    }

    private static void drawText(Graphics2D g, BufferedImage image, ImageOperation.Watermark op) {
        int shortEdge = Math.min(image.getWidth(), image.getHeight());
        int fontPx = Math.max(MIN_FONT_PX, (int) Math.round(shortEdge * FONT_SCALE));
        // Font.SANS_SERIF is a logical family, guaranteed present on every JDK; naming a physical
        // font ("Arial") silently falls back to something else on Linux and shifts the layout.
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontPx));

        FontRenderContext frc = g.getFontRenderContext();
        Rectangle2D bounds = g.getFont().getStringBounds(op.text(), frc);
        int textWidth = (int) Math.ceil(bounds.getWidth());
        int textHeight = (int) Math.ceil(bounds.getHeight());

        int[] xy = anchor(image, op, textWidth, textHeight);
        int baseline = xy[1] + textHeight - (int) Math.ceil(bounds.getMaxY() - bounds.getHeight());

        // Dark shadow first so the mark stays legible over both white snow and black shadow.
        g.setColor(new Color(0, 0, 0, 140));
        g.drawString(op.text(), xy[0] + Math.max(1, fontPx / 20), baseline + Math.max(1, fontPx / 20));
        g.setColor(Color.WHITE);
        g.drawString(op.text(), xy[0], baseline);
    }

    /** Top-left corner for a {@code w x h} mark under the requested anchor and margin. */
    private static int[] anchor(BufferedImage image, ImageOperation.Watermark op, int w, int h) {
        int margin = op.marginPx();
        int right = Math.max(margin, image.getWidth() - w - margin);
        int bottom = Math.max(margin, image.getHeight() - h - margin);
        return switch (op.anchor()) {
            case TOP_LEFT -> new int[] {margin, margin};
            case TOP_RIGHT -> new int[] {right, margin};
            case BOTTOM_LEFT -> new int[] {margin, bottom};
            case BOTTOM_RIGHT -> new int[] {right, bottom};
            case CENTER -> new int[] {
                Math.max(0, (image.getWidth() - w) / 2),
                Math.max(0, (image.getHeight() - h) / 2),
            };
        };
    }
}
