package com.example.pipeline.application.port;

import com.example.pipeline.domain.AggregateSnapshot;

/**
 * Destination for aggregate results (driven port of stage 3).
 *
 * <p>Called from the aggregation dispatcher thread — once at the end of a run, and
 * optionally periodically. Implementations may block on IO: they are off the
 * per-event hot path, but they must never be invoked from inside a fork/join task,
 * because blocking a {@code ForkJoinPool} worker starves the pool.
 */
@FunctionalInterface
public interface AggregateSink {

    /**
     * Publishes a snapshot.
     *
     * @throws RuntimeException wrapped IO failures; the orchestrator counts these as
     *         errors and completes the stage exceptionally rather than losing them
     */
    void publish(AggregateSnapshot snapshot);

    /** Sink that discards everything — for benchmarks and tests. */
    static AggregateSink discarding() {
        return snapshot -> {
            // intentionally empty
        };
    }
}
