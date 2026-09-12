package com.parallelimage.core.progress;

import com.parallelimage.core.util.Preconditions;

/**
 * An immutable progress notification emitted from a fork/join worker.
 *
 * <p>Immutable because it crosses a thread boundary (worker → UI) without any synchronization: safe
 * publication of a fully-initialized record needs no lock.
 *
 * @param batchId   batch correlation id
 * @param jobId     job the event refers to, or {@code null} for batch-level events
 * @param phase     where in the lifecycle this event was raised
 * @param completed number of jobs finished so far in the batch
 * @param total     total jobs in the batch
 * @param detail    short human-readable detail; never a full filesystem path at INFO level
 */
public record ProgressEvent(
        String batchId,
        String jobId,
        Phase phase,
        int completed,
        int total,
        String detail) {

    /** Lifecycle position. Kept coarse: per-pixel or per-tile events would flood the FX thread. */
    public enum Phase {
        BATCH_STARTED,
        JOB_STARTED,
        JOB_COMPLETED,
        JOB_FAILED,
        JOB_CANCELLED,
        BATCH_FINISHED
    }

    public ProgressEvent {
        Preconditions.requireNonBlank(batchId, "batchId");
        Preconditions.requireNonNull(phase, "phase");
        Preconditions.requireNonNegative(completed, "completed");
        Preconditions.requireNonNegative(total, "total");
        detail = detail == null ? "" : detail;
    }

    public static ProgressEvent batchStarted(String batchId, int total) {
        return new ProgressEvent(batchId, null, Phase.BATCH_STARTED, 0, total, "batch started");
    }

    public static ProgressEvent batchFinished(String batchId, int completed, int total) {
        return new ProgressEvent(batchId, null, Phase.BATCH_FINISHED, completed, total, "done");
    }

    /** Fraction in {@code [0, 1]}; {@code 0} when the total is unknown. */
    public double fraction() {
        return total <= 0 ? 0.0d : Math.min(1.0d, (double) completed / total);
    }

    public boolean isBatchLevel() {
        return phase == Phase.BATCH_STARTED || phase == Phase.BATCH_FINISHED;
    }
}
