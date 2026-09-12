package com.parallelimage.core.model;

import com.parallelimage.core.util.Preconditions;
import java.util.Map;

/**
 * Read-mostly descriptive data about a decoded image.
 *
 * <p>This is the value stored in {@link com.parallelimage.core.metadata.MetadataStore} and therefore
 * the value read under {@code StampedLock.tryOptimisticRead()}. Being an immutable {@code record}
 * with only immutable components is <strong>load-bearing</strong>, not stylistic: an optimistic
 * reader may observe a reference published by a concurrent writer, and if that object were mutable
 * the reader could see a half-initialized instance. With immutability the worst case is a
 * <em>stale but whole</em> snapshot, which {@code validate()} then rejects.
 *
 * <p>See {@code docs/ARCHITECTURE.md} §2.2 and {@code docs/adr/0004-stampedlock-for-metadata.md}.
 *
 * @param jobId          owning job
 * @param width          decoded pixel width
 * @param height         decoded pixel height
 * @param formatName     ImageIO informal format name of the source
 * @param sourceBytes    file size on disk
 * @param bufferedType   the {@link java.awt.image.BufferedImage} type constant after normalization
 * @param hasAlpha       whether the source carried an alpha channel
 * @param exif           small subset of EXIF tags; unmodifiable, never contains GPS unless retained
 */
public record ImageMetadata(
        String jobId,
        int width,
        int height,
        String formatName,
        long sourceBytes,
        int bufferedType,
        boolean hasAlpha,
        Map<String, String> exif) {

    public ImageMetadata {
        Preconditions.requireNonBlank(jobId, "jobId");
        Preconditions.requirePositive(width, "width");
        Preconditions.requirePositive(height, "height");
        Preconditions.requireNonBlank(formatName, "formatName");
        Preconditions.requireNonNegative(sourceBytes, "sourceBytes");
        // Map.copyOf both defends against mutation and rejects nulls — required for safe publication.
        exif = exif == null ? Map.of() : Map.copyOf(exif);
    }

    public long pixelCount() {
        return (long) width * (long) height;
    }

    /** Bytes an INT-packed copy of this image occupies on the heap. Used for admission control. */
    public long estimatedHeapBytes() {
        return pixelCount() * 4L;
    }

    public double megapixels() {
        return pixelCount() / 1_000_000.0d;
    }

    /** {@code true} when this image is large enough to be worth Level-2 tile decomposition. */
    public boolean warrantsTiling(long tileThresholdPixels) {
        return pixelCount() > tileThresholdPixels;
    }
}
