package com.parallelimage.core.model;

import com.parallelimage.core.util.Preconditions;
import java.nio.file.Path;
import java.util.UUID;

/**
 * One unit of Level-1 work: transform {@code source} into {@code target} by applying
 * {@code options.operations()}.
 *
 * <p><strong>Immutable.</strong> Status changes produce a new instance via {@link #withStatus} rather
 * than mutating shared state — a mutable status field would be read and written by many fork/join
 * workers and would need synchronization on the hottest path in the system. Publishing an immutable
 * record instead means a racy read yields a stale-but-internally-consistent value, never a torn one.
 * This is the same property {@link com.parallelimage.core.metadata.MetadataStore} relies on for its
 * optimistic reads.
 *
 * @param id       stable identity, also the correlation id in logs
 * @param batchId  groups jobs submitted together
 * @param source   file to read; must exist at submit time
 * @param target   file to write; parent directories are created on demand
 * @param options  pipeline and encoding settings
 * @param status   current lifecycle position
 */
public record ImageJob(
        String id,
        String batchId,
        Path source,
        Path target,
        ProcessingOptions options,
        JobStatus status) {

    public ImageJob {
        Preconditions.requireNonBlank(id, "id");
        Preconditions.requireNonBlank(batchId, "batchId");
        Preconditions.requireNonNull(source, "source");
        Preconditions.requireNonNull(target, "target");
        Preconditions.requireNonNull(options, "options");
        Preconditions.requireNonNull(status, "status");
        if (source.equals(target)) {
            throw new IllegalArgumentException("in-place processing is not supported: " + source);
        }
    }

    /** Factory for a fresh {@link JobStatus#PENDING} job with a generated id. */
    public static ImageJob create(String batchId, Path source, Path target,
            ProcessingOptions options) {
        return new ImageJob(UUID.randomUUID().toString(), batchId, source, target, options,
                JobStatus.PENDING);
    }

    /**
     * Returns a copy in the given status.
     *
     * @throws IllegalStateException if the transition is not legal (see
     *         {@link JobStatus#canTransitionTo})
     */
    public ImageJob withStatus(JobStatus next) {
        Preconditions.requireNonNull(next, "next");
        if (status != next && !status.canTransitionTo(next)) {
            throw new IllegalStateException(
                    "illegal transition " + status + " -> " + next + " for job " + id);
        }
        return new ImageJob(id, batchId, source, target, options, next);
    }

    /** Short label for UI tables and log lines — never the full path (see ARCHITECTURE §2.6). */
    public String displayName() {
        Path name = source.getFileName();
        return name == null ? source.toString() : name.toString();
    }
}
