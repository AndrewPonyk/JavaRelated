package com.parallelimage.core.fork;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Constructs and describes the application's dedicated {@link ForkJoinPool}.
 *
 * <h2>Why not {@code ForkJoinPool.commonPool()}</h2>
 * The common pool is shared with parallel streams, {@code CompletableFuture}, and any library in the
 * JVM that uses it. A single blocking image decode submitted there can stall unrelated work
 * application-wide, and its parallelism cannot be tuned per workload. We own our pool. Corollary:
 * <strong>never call {@code parallelStream()} on the processing path</strong> — it silently lands on
 * the common pool (TECH-NOTES §3.6 A5).
 *
 * <h2>Sizing</h2>
 * Default parallelism is {@code availableProcessors() - 1}. The reserved core is not superstition:
 * the JavaFX render/application thread and Shenandoah's concurrent GC threads both need CPU, and
 * starving the FX thread is immediately visible to the user as a frozen window.
 *
 * <h2>{@code asyncMode = true}</h2>
 * The pool is created in async (FIFO-local) mode. That is the documented setting for pools whose
 * tasks are "event-style" — never joined by the task that forked them. Our batch leaves fit that
 * shape: they are submitted work items rather than parent tasks waiting on children, and FIFO order
 * gives fairer latency across a batch. The nested Level-2 tile tasks <em>do</em> join, but they are
 * created and joined within a single leaf's stack frame, where the local ordering is irrelevant.
 *
 * <p>Thread-safe (stateless factory).
 */
public final class ForkJoinConfig {

    private static final Logger LOG = System.getLogger(ForkJoinConfig.class.getName());

    /** Thread-name prefix; makes stack dumps and profiler output readable. */
    private static final String WORKER_PREFIX = "pip-worker-";

    private static final AtomicInteger POOL_COUNTER = new AtomicInteger();

    private ForkJoinConfig() {
        throw new AssertionError("no instances");
    }

    /** Parallelism to use when the user has not configured one explicitly. */
    public static int defaultParallelism() {
        return Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    }

    /**
     * Creates a dedicated pool.
     *
     * @param parallelism target parallelism; {@code <= 0} means {@link #defaultParallelism()}
     */
    public static ForkJoinPool newPool(int parallelism) {
        int effective = parallelism > 0 ? parallelism : defaultParallelism();
        int poolId = POOL_COUNTER.incrementAndGet();

        ForkJoinPool.ForkJoinWorkerThreadFactory factory = pool -> {
            ForkJoinWorkerThread thread = new ForkJoinWorkerThread(pool) { };
            thread.setName(WORKER_PREFIX + poolId + "-" + thread.getPoolIndex());
            // Image work is CPU-bound and never holds a UI lock; default priority is correct.
            thread.setDaemon(true);
            return thread;
        };

        // A worker's uncaught exception means a leaf failed to convert its own error into a
        // JobOutcome.Failure -- i.e. a bug in our error handling, not a bad input file.
        // Log it loudly rather than letting it vanish into the pool.
        Thread.UncaughtExceptionHandler handler = (thread, error) ->
                LOG.log(Level.ERROR,
                        () -> "uncaught exception on fork/join worker " + thread.getName()
                                + " -- a task leaked an exception instead of returning a Failure",
                        error);

        ForkJoinPool pool = new ForkJoinPool(effective, factory, handler, /* asyncMode */ true);
        LOG.log(Level.INFO, () -> "created ForkJoinPool#" + poolId + " parallelism=" + effective
                + " (cores=" + Runtime.getRuntime().availableProcessors() + ")");
        return pool;
    }

    /** Convenience overload using {@link #defaultParallelism()}. */
    public static ForkJoinPool newPool() {
        return newPool(0);
    }

    /**
     * Orderly shutdown: stop accepting work, wait, then force.
     *
     * <p>Workers are daemon threads so a missed shutdown cannot hang JVM exit, but an explicit
     * drain lets in-flight images finish writing instead of leaving {@code *.tmp} files behind.
     *
     * @return {@code true} if the pool drained within the timeout
     */
    public static boolean shutdownGracefully(ForkJoinPool pool, long timeout, TimeUnit unit) {
        if (pool == null) {
            return true;
        }
        pool.shutdown();
        try {
            if (pool.awaitTermination(timeout, unit)) {
                return true;
            }
            LOG.log(Level.WARNING, "pool did not drain in time; forcing shutdown");
            pool.shutdownNow();
            return false;
        } catch (InterruptedException e) {
            // Restore the flag: the caller above may also be shutting down. Never swallow.
            Thread.currentThread().interrupt();
            pool.shutdownNow();
            return false;
        }
    }

    /**
     * Reports whether Shenandoah is the active collector.
     *
     * <p>Purely informational — the application must run correctly under any collector. Some vendor
     * JDK builds omit Shenandoah entirely, so a hard requirement would be a startup failure for no
     * functional reason (TECH-NOTES §3.6 C1).
     */
    public static boolean isShenandoahActive() {
        try {
            return java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream()
                    .anyMatch(bean -> bean.getName().toLowerCase(java.util.Locale.ROOT)
                            .contains("shenandoah"));
        } catch (RuntimeException e) {
            LOG.log(Level.DEBUG, "could not inspect GC beans", e);
            return false;
        }
    }

    /** One-line summary for the startup log and the UI status bar. */
    public static String describe(ForkJoinPool pool) {
        return "parallelism=%d activeThreads=%d queued=%d steals=%d gc=%s".formatted(
                pool.getParallelism(),
                pool.getActiveThreadCount(),
                pool.getQueuedSubmissionCount(),
                pool.getStealCount(),
                isShenandoahActive() ? "Shenandoah" : "other");
    }
}
