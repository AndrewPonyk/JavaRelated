package com.example.pipeline.infrastructure.concurrent;

import com.example.pipeline.application.port.StageLifecycle;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns every thread pool in the process and implements the orderly shutdown escalation.
 *
 * <p>Four pools, deliberately separate:
 * <ul>
 *   <li><strong>producer</strong> — one thread; generation is sequential by design so
 *       the sequence numbers stay monotonic;</li>
 *   <li><strong>filter</strong> — a fixed pool sized to the CPU count, because the
 *       predicate is CPU-bound and more threads than cores only adds context
 *       switching;</li>
 *   <li><strong>aggregation dispatcher</strong> — one thread whose only job is the
 *       blocking queue read. Keeping it out of the fork/join pool is what stops a
 *       blocked read from consuming a fork/join worker;</li>
 *   <li><strong>fork/join</strong> — a <em>dedicated</em> {@code ForkJoinPool}, never
 *       {@link ForkJoinPool#commonPool()}. The common pool is shared with every
 *       parallel stream in the JVM, and its default parallelism is {@code cores - 1},
 *       which is <em>zero worker threads</em> on a single-core CI runner — work then
 *       runs on the caller and the "parallel" stage is silently sequential.</li>
 * </ul>
 *
 * <p><strong>Why separate pools at all:</strong> sharing one pool between a blocking
 * producer, blocking consumers and fork/join arithmetic is how pipelines deadlock —
 * every thread ends up parked in {@code put} on a full queue with nobody left to drain
 * it. Separation makes that structurally impossible.
 *
 * <p>{@link #shutdownAndAwait(Duration)} implements the canonical escalation:
 * {@code shutdown()} → {@code awaitTermination} → {@code shutdownNow()} →
 * {@code awaitTermination} again, because {@code shutdownNow()} only <em>interrupts</em>
 * and a task that ignores interrupts is still running when it returns.
 */
public final class PipelineExecutors implements StageLifecycle {

    private static final Logger LOG = Logger.getLogger(PipelineExecutors.class.getName());

    /** Share of the total timeout spent on the polite drain before escalating. */
    private static final double DRAIN_SHARE = 0.8d;

    private final ExecutorService producerExecutor;
    private final ExecutorService consumerExecutor;
    private final ExecutorService dispatcherExecutor;
    private final ForkJoinPool forkJoinPool;

    /**
     * @param consumerThreads      fixed pool size for the filter stage, {@code >= 1}
     * @param aggregationParallelism fork/join parallelism, {@code >= 1} (resolve
     *                             {@code 0} to {@code availableProcessors()} before calling)
     */
    public PipelineExecutors(int consumerThreads, int aggregationParallelism) {
        if (consumerThreads < 1) {
            throw new IllegalArgumentException("consumerThreads must be >= 1 but was " + consumerThreads);
        }
        if (aggregationParallelism < 1) {
            throw new IllegalArgumentException(
                    "aggregationParallelism must be >= 1 but was " + aggregationParallelism);
        }
        this.producerExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("pipeline-producer"));
        this.consumerExecutor = Executors.newFixedThreadPool(consumerThreads,
                new NamedThreadFactory("pipeline-filter"));
        this.dispatcherExecutor = Executors.newSingleThreadExecutor(
                new NamedThreadFactory("pipeline-aggregator"));
        this.forkJoinPool = new ForkJoinPool(aggregationParallelism,
                pool -> {
                    ForkJoinWorkerThreadAdapter thread = new ForkJoinWorkerThreadAdapter(pool);
                    thread.setName("pipeline-fj-" + thread.getPoolIndex());
                    return thread;
                },
                (t, throwable) -> LOG.log(Level.SEVERE, "uncaught exception in " + t.getName(), throwable),
                // asyncMode=false: LIFO local queues, correct for recursive fork/join
                // (a worker's most recently forked task is the one still in cache).
                false);
    }

    /** Executor for the single producer task. */
    public ExecutorService producerExecutor() {
        return producerExecutor;
    }

    /** Fixed pool for the filter consumers. */
    public ExecutorService consumerExecutor() {
        return consumerExecutor;
    }

    /** Single-threaded executor for the aggregation dispatcher loop. */
    public ExecutorService dispatcherExecutor() {
        return dispatcherExecutor;
    }

    /** Dedicated pool for {@code AggregationTask}. */
    public ForkJoinPool forkJoinPool() {
        return forkJoinPool;
    }

    @Override
    public boolean shutdownAndAwait(Duration timeout) throws InterruptedException {
        long totalNanos = timeout.toNanos();
        long drainNanos = (long) (totalNanos * DRAIN_SHARE);

        List<ExecutorService> executors = allExecutors();
        for (ExecutorService executor : executors) {
            executor.shutdown();
        }
        forkJoinPool.shutdown();

        long deadline = System.nanoTime() + drainNanos;
        boolean terminated = true;
        for (ExecutorService executor : executors) {
            terminated &= awaitUntil(executor, deadline);
        }
        terminated &= forkJoinPool.awaitTermination(remainingNanos(deadline), TimeUnit.NANOSECONDS);
        if (terminated) {
            LOG.fine("all pools terminated cleanly");
            return true;
        }

        LOG.warning("pools still running after the drain window; interrupting");
        forceShutdown();
        // shutdownNow() only interrupts: wait again, or we would report termination that
        // has not happened for tasks that swallow interrupts.
        long escalationDeadline = System.nanoTime() + (totalNanos - drainNanos);
        boolean afterEscalation = true;
        for (ExecutorService executor : executors) {
            afterEscalation &= awaitUntil(executor, escalationDeadline);
        }
        afterEscalation &= forkJoinPool.awaitTermination(remainingNanos(escalationDeadline), TimeUnit.NANOSECONDS);
        if (!afterEscalation) {
            LOG.severe("pools did not terminate even after interruption; threads are stuck "
                    + "in an uninterruptible section");
        }
        return false;
    }

    @Override
    public void forceShutdown() {
        for (ExecutorService executor : allExecutors()) {
            List<Runnable> dropped = executor.shutdownNow();
            if (!dropped.isEmpty()) {
                LOG.log(Level.WARNING, "{0} queued task(s) never started", dropped.size());
            }
        }
        forkJoinPool.shutdownNow();
    }

    @Override
    public void close() {
        // Idempotent: shutdownNow() on an already-terminated pool is a no-op, so close()
        // is safe in a try-with-resources even after an explicit shutdownAndAwait().
        forceShutdown();
    }

    private List<ExecutorService> allExecutors() {
        List<ExecutorService> executors = new ArrayList<>(3);
        executors.add(producerExecutor);
        executors.add(consumerExecutor);
        executors.add(dispatcherExecutor);
        return executors;
    }

    private static boolean awaitUntil(ExecutorService executor, long deadlineNanos) throws InterruptedException {
        return executor.awaitTermination(remainingNanos(deadlineNanos), TimeUnit.NANOSECONDS);
    }

    private static long remainingNanos(long deadlineNanos) {
        return Math.max(0L, deadlineNanos - System.nanoTime());
    }

    /**
     * Fork/join worker that exists only so the threads can be given readable names.
     *
     * <p>{@code ForkJoinWorkerThread}'s constructor is protected, so naming its threads
     * requires a subclass — worth it for the same reason as
     * {@link NamedThreadFactory}: {@code pipeline-fj-3} in a stack trace beats
     * {@code ForkJoinPool-2-worker-3}.
     */
    private static final class ForkJoinWorkerThreadAdapter extends ForkJoinWorkerThread {
        ForkJoinWorkerThreadAdapter(ForkJoinPool pool) {
            super(pool);
        }
    }
}
