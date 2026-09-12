package com.parallelimage.core.pipeline;

import com.parallelimage.core.util.Preconditions;
import java.nio.file.Path;

/**
 * A single declarative step in an image processing pipeline.
 *
 * <p><strong>Sealed on purpose.</strong> {@link OperationPipeline} dispatches with a pattern-matching
 * {@code switch} that has <em>no</em> {@code default} branch. Adding a new permitted subtype
 * therefore produces a compile error at every dispatch site until it is handled — which is exactly
 * the safety property we want in a codebase where a silently-ignored operation would look like a
 * subtle rendering bug rather than a missing feature.
 *
 * <p>Operations are immutable value objects describing <em>what</em> to do. The <em>how</em> lives in
 * {@link com.parallelimage.core.filter}, keeping parameters serializable and free of pixel buffers
 * (see {@code docs/TECH-NOTES.md} §3.6 B2 — never put an array in a record).
 */
public sealed interface ImageOperation {

    /** Stable, human-readable name used in logs, the UI, and persisted pipelines. */
    String name();

    /**
     * Whether this operation can be decomposed into independent tiles and run through
     * {@link com.parallelimage.core.fork.TileProcessingAction}.
     *
     * <p>Operations that change the output geometry ({@link Resize}) or need a single
     * {@link java.awt.Graphics2D} context over the whole surface ({@link Watermark}) are not
     * tileable and run whole-image on the calling worker.
     */
    default boolean tileable() {
        return true;
    }

    // ────────────────────────────── geometry ──────────────────────────────

    /**
     * Bilinear rescale. Not tileable: the output has different dimensions, and tiling a resample
     * requires overlapping source reads plus per-tile coordinate mapping.
     *
     * @param targetWidth        desired width in pixels
     * @param targetHeight       desired height in pixels
     * @param preserveAspectRatio when {@code true}, treat the target as a bounding box and fit inside
     */
    record Resize(int targetWidth, int targetHeight, boolean preserveAspectRatio)
            implements ImageOperation {

        public Resize {
            Preconditions.requirePositive(targetWidth, "targetWidth");
            Preconditions.requirePositive(targetHeight, "targetHeight");
        }

        /** Fit-inside-a-box convenience factory. */
        public static Resize boundingBox(int maxEdge) {
            return new Resize(maxEdge, maxEdge, true);
        }

        @Override
        public String name() {
            return "resize";
        }

        @Override
        public boolean tileable() {
            return false;
        }
    }

    // ────────────────────────────── tileable filters ──────────────────────────────

    /** Luma conversion using ITU-R BT.709 coefficients. Perfectly tileable: pixel-local. */
    record Grayscale() implements ImageOperation {
        @Override
        public String name() {
            return "grayscale";
        }
    }

    /**
     * Separable-free box blur with a square kernel of side {@code 2 * radius + 1}.
     *
     * <p>Tileable <em>because</em> the kernel reads its halo from the immutable source image while
     * writing only into its own destination tile — no synchronization required and no seam
     * artefacts. Read {@code docs/ARCHITECTURE.md} §2.3 "Invariants" before changing this.
     *
     * @param radius kernel radius in pixels, 1..64
     */
    record BoxBlur(int radius) implements ImageOperation {

        public BoxBlur {
            if (radius < 1 || radius > 64) {
                throw new IllegalArgumentException("radius must be within [1, 64] but was " + radius);
            }
        }

        @Override
        public String name() {
            return "blur";
        }
    }

    /**
     * Unsharp-style 3&times;3 sharpen.
     *
     * @param amount 0.0 = no-op, 1.0 = standard, up to 5.0 for aggressive
     */
    record Sharpen(double amount) implements ImageOperation {

        public Sharpen {
            Preconditions.requireInRange(amount, 0.0d, 5.0d, "amount");
        }

        @Override
        public String name() {
            return "sharpen";
        }
    }

    // ────────────────────────────── whole-image ──────────────────────────────

    /** Where to place a watermark relative to the image bounds. */
    enum Anchor {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER
    }

    /**
     * Text and/or image watermark composited with alpha.
     *
     * <p>Not tileable: uses a single {@link java.awt.Graphics2D} over the whole surface, and
     * {@code Graphics2D} instances are not thread-safe.
     *
     * @param text     text to draw, or {@code null} for image-only
     * @param overlay  PNG to composite, or {@code null} for text-only
     * @param anchor   placement
     * @param opacity  0.0 (invisible) .. 1.0 (opaque)
     * @param marginPx inset from the anchored edges
     */
    record Watermark(String text, Path overlay, Anchor anchor, double opacity, int marginPx)
            implements ImageOperation {

        public Watermark {
            if ((text == null || text.isBlank()) && overlay == null) {
                throw new IllegalArgumentException("watermark needs either text or an overlay image");
            }
            Preconditions.requireNonNull(anchor, "anchor");
            Preconditions.requireInRange(opacity, 0.0d, 1.0d, "opacity");
            Preconditions.requireNonNegative(marginPx, "marginPx");
        }

        public static Watermark ofText(String text) {
            return new Watermark(text, null, Anchor.BOTTOM_RIGHT, 0.55d, 24);
        }

        @Override
        public String name() {
            return "watermark";
        }

        @Override
        public boolean tileable() {
            return false;
        }
    }

    // ────────────────────────────── native / ML ──────────────────────────────

    /** Which OpenCV enhancement to request from the {@code ImageEnhancer} SPI. */
    enum EnhanceMode {
        /** Contrast Limited Adaptive Histogram Equalization. */
        CLAHE,
        /** Non-local-means denoise. */
        DENOISE,
        /** DNN super-resolution (heaviest; requires a model file). */
        SUPER_RESOLUTION
    }

    /**
     * ML-based enhancement delegated to the native OpenCV bridge via
     * {@link com.parallelimage.core.spi.ImageEnhancer}.
     *
     * <p>Degrades gracefully: when no native implementation is registered, the
     * {@code PassthroughEnhancer} runs instead and the batch continues with a {@code WARNING}.
     *
     * @param mode     which algorithm
     * @param strength algorithm-specific intensity, 0.0..1.0
     */
    record Enhance(EnhanceMode mode, double strength) implements ImageOperation {

        public Enhance {
            Preconditions.requireNonNull(mode, "mode");
            Preconditions.requireInRange(strength, 0.0d, 1.0d, "strength");
        }

        @Override
        public String name() {
            return "enhance:" + mode.name().toLowerCase(java.util.Locale.ROOT);
        }

        /** Not tileable: the native call is made once per image to amortize JNI transition cost. */
        @Override
        public boolean tileable() {
            return false;
        }
    }
}
