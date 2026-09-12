package com.parallelimage.core.engine;

import com.parallelimage.core.fork.AdaptiveThrottle;
import com.parallelimage.core.fork.ForkJoinConfig;
import com.parallelimage.core.metadata.MetadataStore;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.ForkJoinPool;

/**
 * A point-in-time snapshot of engine and pool health, for the UI status bar and the CLI summary.
 *
 * <p>Immutable, so it crosses the worker → JavaFX thread boundary with no synchronization.
 *
 * <h2>Why {@code stealCount} is the number to watch</h2>
 * It is the single best indicator that the two-level decomposition is doing its job. A batch of
 * similar images produces few steals — every worker has its own images and never runs dry. A batch
 * containing one enormous panorama produces <em>many</em> steals near the end, as workers that have
 * finished their own images pull tile-level subtasks out of the panorama's queue. A batch that
 * <em>should</em> show that spike and doesn't is a sign the Level-2 threshold is too coarse and the
 * panorama is being processed by one thread after all (ARCHITECTURE §2.4).
 *
 * @param parallelism        configured pool parallelism
 * @param activeThreads      workers currently executing a task
 * @param runningThreads     workers not blocked in a join or a managed block
 * @param queuedTasks        tasks queued in worker deques (Level-1 + Level-2)
 * @param queuedSubmissions  batches submitted from outside the pool and not yet started
 * @param stealCount         cumulative work-steal count for the pool's lifetime
 * @param metadataEntries    records held in the {@link MetadataStore}
 * @param optimisticReadRate fraction of optimistic metadata reads that validated
 * @param shenandoahActive   whether Shenandoah is the active collector
 * @param heapUtilisation    fraction of max heap currently used — the same sample
 *                           {@link AdaptiveThrottle} uses to decide whether to stop splitting tiles,
 *                           reused here rather than polled a second time
 * @param gcTimeMillis       cumulative time spent in garbage collection since JVM start, summed
 *                           across all collectors
 */
public record EngineStats(
        int parallelism,
        int activeThreads,
        int runningThreads,
        long queuedTasks,
        int queuedSubmissions,
        long stealCount,
        int metadataEntries,
        double optimisticReadRate,
        boolean shenandoahActive,
        double heapUtilisation,
        long gcTimeMillis) {

    public static final EngineStats UNAVAILABLE =
            new EngineStats(0, 0, 0, 0L, 0, 0L, 0, 1.0d, false, 0.0d, 0L);

    /** Reads the live counters. Cheap, but not free — poll it, do not call it per image. */
    public static EngineStats sample(ForkJoinPool pool, MetadataStore store) {
        if (pool == null) {
            return UNAVAILABLE;
        }
        return new EngineStats(
                pool.getParallelism(),
                pool.getActiveThreadCount(),
                pool.getRunningThreadCount(),
                pool.getQueuedTaskCount(),
                pool.getQueuedSubmissionCount(),
                pool.getStealCount(),
                store == null ? 0 : store.size(),
                store == null ? 1.0d : store.optimisticSuccessRate(),
                ForkJoinConfig.isShenandoahActive(),
                AdaptiveThrottle.sample().heapUtilisation(),
                totalGcTimeMillis());
    }

    private static long totalGcTimeMillis() {
        long total = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long time = bean.getCollectionTime();
            if (time > 0) {
                total += time;
            }
        }
        return total;
    }

    /** Rough utilisation, for a progress-bar tint. Clamped because the counters are sampled, not
     * synchronized, and can briefly disagree. */
    public double utilisation() {
        if (parallelism <= 0) {
            return 0.0d;
        }
        return Math.min(1.0d, activeThreads / (double) parallelism);
    }

    /** One-line form for the status bar. */
    public String summary() {
        return ("%d/%d workers · %d queued · %d steals · metadata %d (%.0f%% optimistic) · "
                + "heap %.0f%% · gc %dms · %s").formatted(
                activeThreads, parallelism, queuedTasks, stealCount, metadataEntries,
                optimisticReadRate * 100.0d, heapUtilisation * 100.0d, gcTimeMillis,
                shenandoahActive ? "Shenandoah" : "default GC");
    }
}
