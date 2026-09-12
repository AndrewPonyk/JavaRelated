package com.parallelimage.core.filter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-thread, size-bucketed pool of scratch {@code int[]} arrays for tile kernels that need
 * working buffers beyond the source/destination pixels themselves — currently
 * {@link BoxBlurFilter}'s scratch band and column accumulators.
 *
 * <h2>Why {@code ThreadLocal}, not a shared pool</h2>
 * Fork/join leaves run on the small, fixed set of long-lived worker threads
 * {@code ForkJoinConfig.newPool()} creates once and never resizes. A thread-confined pool reuses
 * the same handful of arrays for a whole batch's lifetime with no cross-thread contention or
 * locking — a shared pool would need synchronization on every borrow/release for no benefit,
 * since the worker set is already small and stable.
 *
 * <h2>Why size-bucketed rather than one array per thread</h2>
 * Tile dimensions vary per call — root tiles are large, deep leaves are small, and the last row or
 * column of an image is often a different size from the rest — so a single reused array would
 * need to be sized for the largest tile ever seen and be mostly wasted space for every smaller
 * one. Bucketing by power-of-two capacity keeps a handful of appropriately-sized arrays per thread
 * instead of one oversized array or constant reallocation.
 *
 * <p><strong>Borrowed arrays may contain stale data from a previous release.</strong> Callers that
 * need zeroed state (e.g. an accumulator that is added into) must clear the range they use
 * themselves; callers that fully overwrite every slot before reading it back (e.g. a scratch band)
 * do not need to.
 */
public final class TileBufferPool {

    /** {@code 1 << 30} is the largest power-of-two array size that fits in a positive {@code int}. */
    private static final int MAX_BUCKET = 30;

    private static final int MAX_ARRAYS_PER_BUCKET = 4;

    private static final ThreadLocal<Map<Integer, Deque<int[]>>> BUCKETS =
            ThreadLocal.withInitial(HashMap::new);

    private TileBufferPool() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns an {@code int[]} of length at least {@code minLength}, reused from this thread's pool
     * if one of adequate size is available, freshly allocated otherwise.
     */
    public static int[] borrow(int minLength) {
        int bucket = bucketFor(minLength);
        Deque<int[]> pool = BUCKETS.get().get(bucket);
        if (pool != null) {
            int[] array = pool.poll();
            if (array != null) {
                return array;
            }
        }
        return new int[1 << bucket];
    }

    /**
     * Returns an array previously obtained from {@link #borrow(int)} on this same thread so a later
     * call can reuse it. Arrays not shaped like one of {@code borrow}'s own allocations (wrong
     * length, or {@code null}) are silently dropped rather than pooled.
     */
    public static void release(int[] array) {
        if (array == null) {
            return;
        }
        int length = array.length;
        if (length == 0 || (length & (length - 1)) != 0) {
            return;
        }
        int bucket = Integer.numberOfTrailingZeros(length);
        Deque<int[]> pool = BUCKETS.get().computeIfAbsent(bucket, b -> new ArrayDeque<>(MAX_ARRAYS_PER_BUCKET));
        if (pool.size() < MAX_ARRAYS_PER_BUCKET) {
            pool.push(array);
        }
    }

    private static int bucketFor(int minLength) {
        int length = Math.max(1, minLength);
        int bucket = Integer.SIZE - Integer.numberOfLeadingZeros(length - 1);
        return Math.min(bucket, MAX_BUCKET);
    }
}
