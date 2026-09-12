package com.example.pipeline.application.port;

import com.example.pipeline.domain.MetricsSnapshot;

/**
 * Write-heavy, read-rare counters shared by every stage.
 *
 * <p><strong>Contract:</strong> all methods must be thread-safe and must not block
 * or allocate meaningfully — they sit on the hot path and are called once per batch
 * (never once per event, which is why the methods take counts rather than being
 * called in a loop).
 */
public interface MetricsRecorder {

    /** Stage 1 handed {@code events} events, in {@code batches} messages, downstream. */
    void recordProduced(long events, long batches);

    /** Stage 2 decided about a batch: {@code passed} forwarded, {@code rejected} dropped. */
    void recordFiltered(long passed, long rejected);

    /** Stage 3 folded {@code events} events, in {@code batches} messages, into the snapshot. */
    void recordAggregated(long events, long batches);

    /** A counted, non-fatal failure in {@code stage}. Visible in the final report. */
    void recordError(String stage);

    /** Time a producer spent blocked on a full channel — the bottleneck signal. */
    void recordQueueBlocked(String queueName, long nanos);

    /**
     * Copy of the current counters.
     *
     * <p>Not an atomic instant across fields; see {@link MetricsSnapshot}.
     */
    MetricsSnapshot snapshot();

    /** Discards every counter — for reuse between test cases, not for production use. */
    void reset();

    /** No-op recorder for tests and for benchmarking without observability overhead. */
    static MetricsRecorder noOp() {
        return new MetricsRecorder() {
            @Override
            public void recordProduced(long events, long batches) {
                // intentionally empty
            }

            @Override
            public void recordFiltered(long passed, long rejected) {
                // intentionally empty
            }

            @Override
            public void recordAggregated(long events, long batches) {
                // intentionally empty
            }

            @Override
            public void recordError(String stage) {
                // intentionally empty
            }

            @Override
            public void recordQueueBlocked(String queueName, long nanos) {
                // intentionally empty
            }

            @Override
            public MetricsSnapshot snapshot() {
                return MetricsSnapshot.empty();
            }

            @Override
            public void reset() {
                // intentionally empty
            }
        };
    }
}
