package com.parallelimage.core.util;

import java.util.concurrent.TimeUnit;

/**
 * Minimal elapsed-time measurement based on {@link System#nanoTime()}.
 *
 * <p><strong>Not thread-safe</strong> — create one per task. {@code nanoTime} is monotonic but only
 * comparable within a single JVM; never persist or transmit raw values.
 */
public final class StopWatch {

    private final long startNanos;

    private StopWatch(long startNanos) {
        this.startNanos = startNanos;
    }

    public static StopWatch started() {
        return new StopWatch(System.nanoTime());
    }

    public long elapsedNanos() {
        return System.nanoTime() - startNanos;
    }

    public long elapsedMillis() {
        return TimeUnit.NANOSECONDS.toMillis(elapsedNanos());
    }

    /**
     * Throughput in megapixels per second — the only performance number worth comparing across
     * machines and batches (see {@code docs/TECH-NOTES.md} §3.2).
     */
    public double megapixelsPerSecond(long pixels) {
        long nanos = elapsedNanos();
        if (nanos <= 0L) {
            return 0.0d;
        }
        return (pixels / 1_000_000.0d) / (nanos / 1_000_000_000.0d);
    }

    @Override
    public String toString() {
        return elapsedMillis() + " ms";
    }
}
