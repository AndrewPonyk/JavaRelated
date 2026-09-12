package com.parallelimage.core.progress;

/**
 * Sink for {@link ProgressEvent}s raised by fork/join workers.
 *
 * <h2>Implementation contract</h2>
 * <ol>
 *   <li><b>Thread-safe.</b> One listener instance is called concurrently by every worker in the
 *       pool. There is no serialization and no ordering guarantee between events for different
 *       jobs.</li>
 *   <li><b>Fast and non-blocking.</b> The call happens on a worker thread that would otherwise be
 *       processing pixels. Do not perform I/O, acquire contended locks, or update a UI directly:
 *       hand the event to a queue (see {@code pip-ui}'s {@code ProgressBridge}, which coalesces to
 *       ≤30 Hz before touching the JavaFX thread).</li>
 *   <li><b>Never throw.</b> An exception here would propagate out of {@code compute()} and abort a
 *       batch for a cosmetic reason. Implementations swallow and log their own failures.</li>
 * </ol>
 */
@FunctionalInterface
public interface ProgressListener {

    /** Discards everything. Used by tests and the non-interactive CLI. */
    ProgressListener NO_OP = event -> { };

    void onProgress(ProgressEvent event);
}
