package com.parallelimage.core.model;

import com.parallelimage.core.pipeline.ImageOperation;
import com.parallelimage.core.util.Preconditions;
import java.util.List;

/**
 * Immutable configuration for one batch: what to do, how finely to split it, and how to encode
 * the result.
 *
 * <p>Validated at construction so an invalid configuration can never reach a fork/join worker,
 * where the resulting exception would surface on a different thread with a confusing stack trace.
 *
 * @param operations              ordered pipeline; may be empty (a pure format conversion)
 * @param outputFormat            ImageIO informal format name: {@code png}, {@code jpg}, {@code bmp}…
 * @param quality                 lossy encoder quality 0.0..1.0; ignored for lossless formats
 * @param tileThresholdPixels     Level-2 leaf size. Below this, a tile is processed sequentially.
 *                                Target 100k–1M; see {@code docs/PERFORMANCE.md} and
 *                                {@code docs/TECH-NOTES.md} §3.6 A4.
 * @param batchThresholdJobs      Level-1 leaf size in jobs.
 * @param stripMetadata           drop EXIF (including GPS) on write — privacy-by-default
 * @param overwriteExisting       when {@code false}, a job whose target exists is skipped
 * @param maxPixelsPerImage       decode-bomb guard, checked against the header before allocating
 */
public record ProcessingOptions(
        List<ImageOperation> operations,
        String outputFormat,
        float quality,
        long tileThresholdPixels,
        int batchThresholdJobs,
        boolean stripMetadata,
        boolean overwriteExisting,
        long maxPixelsPerImage) {

    /**
     * Calibrated via {@code benchmarks/.../TileThresholdBenchmark} (see {@code docs/PERFORMANCE.md}):
     * across a {@code parallelism} sweep of 1/2/4/8/16 on a 4096x4096 box blur, 262_144 beat the
     * previous default of 65_536 at every level (e.g. 143 vs 182 ms/op at parallelism=4, 117 vs 131
     * ms/op at parallelism=8) and tied it within measurement error at parallelism=16.
     */
    public static final long DEFAULT_TILE_THRESHOLD_PIXELS = 262_144L;

    /**
     * Calibrated via {@code benchmarks/.../BatchThresholdBenchmark} (see {@code docs/PERFORMANCE.md}):
     * across the same parallelism sweep on a 64-job batch, 4 beat the previous default of 8 at
     * every level (e.g. 192 vs 219 ms/op at parallelism=4, 141 vs 145 ms/op at parallelism=8, 94 vs
     * 118 ms/op at parallelism=16) and had the lowest aggregate ms/op across the whole sweep.
     */
    public static final int DEFAULT_BATCH_THRESHOLD_JOBS = 4;

    /** 200 MP — well beyond any real photograph, far below an OOM on an 8 GB heap. */
    public static final long DEFAULT_MAX_PIXELS = 200_000_000L;

    public ProcessingOptions {
        Preconditions.requireNonNull(operations, "operations");
        Preconditions.requireNonBlank(outputFormat, "outputFormat");
        Preconditions.requireInRange(quality, 0.0d, 1.0d, "quality");
        Preconditions.requirePositive(tileThresholdPixels, "tileThresholdPixels");
        Preconditions.requirePositive(batchThresholdJobs, "batchThresholdJobs");
        Preconditions.requirePositive(maxPixelsPerImage, "maxPixelsPerImage");
        // Defensive copy: a record component that is a mutable collection is a footgun.
        operations = List.copyOf(operations);
        outputFormat = outputFormat.toLowerCase(java.util.Locale.ROOT).replace("jpeg", "jpg");
    }

    /** Sensible defaults: PNG output, no operations, privacy-preserving. */
    public static ProcessingOptions defaults() {
        return builder().build();
    }

    public boolean isLossy() {
        return "jpg".equals(outputFormat) || "webp".equals(outputFormat);
    }

    /** {@code true} if any operation needs the alpha channel preserved. */
    public boolean requiresAlpha() {
        return operations.stream().anyMatch(op -> op instanceof ImageOperation.Watermark);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Copy-with semantics for the common case of swapping the operation list. */
    public ProcessingOptions withOperations(List<ImageOperation> newOperations) {
        return new ProcessingOptions(newOperations, outputFormat, quality, tileThresholdPixels,
                batchThresholdJobs, stripMetadata, overwriteExisting, maxPixelsPerImage);
    }

    /**
     * Fluent builder. Records give us immutability and equality for free but an 8-argument canonical
     * constructor is unreadable at call sites, so callers use this instead.
     *
     * <p>Not thread-safe; build on one thread, share the resulting record freely.
     */
    public static final class Builder {
        private List<ImageOperation> operations = List.of();
        private String outputFormat = "png";
        private float quality = 0.9f;
        private long tileThresholdPixels = DEFAULT_TILE_THRESHOLD_PIXELS;
        private int batchThresholdJobs = DEFAULT_BATCH_THRESHOLD_JOBS;
        private boolean stripMetadata = true;
        private boolean overwriteExisting = false;
        private long maxPixelsPerImage = DEFAULT_MAX_PIXELS;

        private Builder() {
        }

        public Builder operations(List<ImageOperation> value) {
            this.operations = value;
            return this;
        }

        public Builder operations(ImageOperation... value) {
            this.operations = List.of(value);
            return this;
        }

        public Builder outputFormat(String value) {
            this.outputFormat = value;
            return this;
        }

        public Builder quality(float value) {
            this.quality = value;
            return this;
        }

        public Builder tileThresholdPixels(long value) {
            this.tileThresholdPixels = value;
            return this;
        }

        public Builder batchThresholdJobs(int value) {
            this.batchThresholdJobs = value;
            return this;
        }

        public Builder stripMetadata(boolean value) {
            this.stripMetadata = value;
            return this;
        }

        public Builder overwriteExisting(boolean value) {
            this.overwriteExisting = value;
            return this;
        }

        public Builder maxPixelsPerImage(long value) {
            this.maxPixelsPerImage = value;
            return this;
        }

        public ProcessingOptions build() {
            return new ProcessingOptions(operations, outputFormat, quality, tileThresholdPixels,
                    batchThresholdJobs, stripMetadata, overwriteExisting, maxPixelsPerImage);
        }
    }
}
