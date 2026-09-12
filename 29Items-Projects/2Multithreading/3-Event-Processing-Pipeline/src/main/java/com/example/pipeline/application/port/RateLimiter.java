package com.example.pipeline.application.port;

/**
 * Paces event generation to a target rate (driven port of stage 1).
 *
 * <p>The configured rate is an <em>upper bound</em>, never a guarantee: sleep
 * granularity on a general-purpose OS is 1–15 ms, so a limiter can only promise
 * "no faster than". Acquire per <em>batch</em> rather than per event — a
 * per-event sleep on Windows caps throughput around 60–100 events/s regardless of
 * the configured rate.
 */
@FunctionalInterface
public interface RateLimiter {

    /**
     * Blocks until {@code permits} events may be emitted.
     *
     * @param permits number of events about to be generated, {@code >= 1}
     * @throws InterruptedException if interrupted while waiting; callers must
     *         restore the interrupt flag and stop producing
     */
    void acquire(int permits) throws InterruptedException;

    /** Limiter that never waits — used when the configured rate is {@code 0} (saturation). */
    static RateLimiter unlimited() {
        return permits -> {
            // no pacing: the pipeline runs as fast as backpressure allows
        };
    }
}
