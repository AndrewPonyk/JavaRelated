package com.parallelimage.core.fork;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

/**
 * Samples heap occupancy and decides whether {@link TileProcessingAction} should stop splitting.
 *
 * <h2>Why the split/leaf decision, not the pool</h2>
 * {@link ForkJoinConfig#newPool()} fixes parallelism at construction; a live {@code ForkJoinPool}
 * cannot be resized. There is therefore no lever to "throttle the pool" once it is running — the
 * only place adaptive behaviour can act is the decision {@code TileProcessingAction.compute()}
 * already makes on every tile: split, or run as a leaf. Under heap pressure, treating more tiles
 * as leaves trades away some parallelism for fewer concurrently-live tile-sized scratch buffers,
 * giving the collector room to catch up before an {@link OutOfMemoryError} does it for us.
 *
 * <h2>Why a short-lived cache and not a live poll on every call</h2>
 * A large image's tile tree calls the split decision thousands of times.
 * {@link MemoryMXBean#getHeapMemoryUsage()} is cheap but not free, and heap occupancy does not
 * change meaningfully faster than a few milliseconds, so a short TTL cache amortizes the polling
 * cost without meaningfully staling the answer.
 *
 * <p>Thread-safe: the cached sample is a single field, replaced wholesale under a lock rather than
 * mutated in place, and read without one.
 */
public final class AdaptiveThrottle {

    /** Cache lifetime for a heap sample; short enough to track real pressure, long enough to matter. */
    private static final long CACHE_TTL_NANOS = 5_000_000L;

    /** Heap occupancy fraction at/above which splitting should stop. */
    private static final double THROTTLE_UTILISATION = 0.85d;

    private static final MemoryMXBean MEMORY_BEAN = ManagementFactory.getMemoryMXBean();

    private static volatile Sample cached;

    private AdaptiveThrottle() {
        throw new AssertionError("no instances");
    }

    /**
     * A point-in-time heap-utilisation sample. Shared with {@code EngineStats} so the two never
     * poll {@link MemoryMXBean} independently.
     *
     * @param timestampNanos  {@link System#nanoTime()} when this sample was taken
     * @param heapUtilisation fraction of max heap used, clamped to {@code [0, 1]}
     */
    public record Sample(long timestampNanos, double heapUtilisation) { }

    /** {@code true} when heap occupancy is high enough that splitting should stop. */
    public static boolean shouldThrottleSplitting() {
        return sample().heapUtilisation() >= THROTTLE_UTILISATION;
    }

    /** Returns the current sample, refreshing it first if the cache has expired. */
    public static Sample sample() {
        Sample snapshot = cached;
        long now = System.nanoTime();
        if (snapshot == null || now - snapshot.timestampNanos() >= CACHE_TTL_NANOS) {
            return refresh(now);
        }
        return snapshot;
    }

    private static synchronized Sample refresh(long now) {
        Sample snapshot = cached;
        if (snapshot != null && now - snapshot.timestampNanos() < CACHE_TTL_NANOS) {
            // Another thread refreshed while this one waited for the lock.
            return snapshot;
        }
        MemoryUsage usage = MEMORY_BEAN.getHeapMemoryUsage();
        long max = usage.getMax();
        double utilisation = max <= 0 ? 0.0d : Math.min(1.0d, usage.getUsed() / (double) max);
        Sample fresh = new Sample(now, utilisation);
        cached = fresh;
        return fresh;
    }
}
