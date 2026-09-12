package com.example.pipeline.application.stage;

import com.example.pipeline.application.port.AggregateRepository;
import com.example.pipeline.application.port.AggregateSink;
import com.example.pipeline.application.port.MessageChannel;
import com.example.pipeline.application.port.MetricsRecorder;
import com.example.pipeline.domain.AggregateResult;
import com.example.pipeline.domain.AggregateSnapshot;
import com.example.pipeline.domain.EventBatch;
import com.example.pipeline.domain.PipelineMessage;
import com.example.pipeline.domain.PoisonPill;
import com.example.pipeline.domain.StageStats;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stage 3 — folds surviving events into per-sensor aggregates using a
 * {@code ForkJoinPool}.
 *
 * <p>Two roles, deliberately on different threads:
 * <ul>
 *   <li>a single <em>dispatcher</em> thread owns the queue read and the running
 *       snapshot — so no lock is needed for either;</li>
 *   <li>the {@code ForkJoinPool} does the arithmetic via {@link AggregationTask}.</li>
 * </ul>
 * Keeping the (blocking) queue read off the fork/join workers is what prevents pool
 * starvation — the classic way to make a fork/join stage mysteriously stall.
 *
 * <p>The pool is <em>dedicated</em>, never {@link ForkJoinPool#commonPool()}: the
 * common pool is shared with every parallel stream in the JVM and its parallelism
 * is {@code cores - 1}, which on a single-core CI runner is zero workers and
 * caller-thread execution.
 */
public final class AggregationStage {

    /** Stage name used in stats, metrics and log lines. */
    public static final String NAME = "aggregation";

    private static final Logger LOG = Logger.getLogger(AggregationStage.class.getName());

    private final MessageChannel input;
    private final ForkJoinPool forkJoinPool;
    private final Executor dispatcherExecutor;
    private final AggregateSink sink;
    private final AggregateRepository repository;
    private final MetricsRecorder metrics;
    private final int sequentialThreshold;
    private final Duration pollTimeout;
    private final AtomicBoolean stopRequested = new AtomicBoolean();
    private final AtomicReference<AggregateSnapshot> liveSnapshot =
            new AtomicReference<>(AggregateSnapshot.empty());

    /**
     * @param input               upstream channel (queue #2)
     * @param forkJoinPool        dedicated pool for the aggregation arithmetic
     * @param dispatcherExecutor  single-threaded executor for the queue-reading loop
     * @param sink                where the final snapshot is published
     * @param repository          where the final snapshot is stored
     * @param metrics             counters
     * @param sequentialThreshold {@link AggregationTask} cutoff, {@code >= 1}
     * @param pollTimeout         how long the dispatcher waits before re-checking the stop flag
     */
    public AggregationStage(MessageChannel input, ForkJoinPool forkJoinPool, Executor dispatcherExecutor,
                            AggregateSink sink, AggregateRepository repository, MetricsRecorder metrics,
                            int sequentialThreshold, Duration pollTimeout) {
        this.input = Objects.requireNonNull(input, "input");
        this.forkJoinPool = Objects.requireNonNull(forkJoinPool, "forkJoinPool");
        this.dispatcherExecutor = Objects.requireNonNull(dispatcherExecutor, "dispatcherExecutor");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.pollTimeout = Objects.requireNonNull(pollTimeout, "pollTimeout");
        if (sequentialThreshold < 1) {
            throw new IllegalArgumentException("sequentialThreshold must be >= 1 but was " + sequentialThreshold);
        }
        if (pollTimeout.isZero() || pollTimeout.isNegative()) {
            throw new IllegalArgumentException("pollTimeout must be positive but was " + pollTimeout);
        }
        this.sequentialThreshold = sequentialThreshold;
    }

    /**
     * Starts the dispatcher loop.
     *
     * @return a future completing with the stats and the final snapshot
     */
    public CompletableFuture<AggregationOutcome> startAsync() {
        return CompletableFuture.supplyAsync(this::run, dispatcherExecutor);
    }

    /** Cooperative stop; the dispatcher notices within one {@code pollTimeout}. */
    public void requestStop() {
        stopRequested.set(true);
    }

    /**
     * Snapshot as of the last completed batch — safe to read from any thread.
     *
     * <p>Used by the live dashboard. Because it is published through an
     * {@code AtomicReference} to an immutable value, readers never see a torn map.
     */
    public AggregateSnapshot currentSnapshot() {
        return liveSnapshot.get();
    }

    /** Package-private so tests can drive the dispatcher synchronously. */
    AggregationOutcome run() {
        long startNanos = System.nanoTime();
        AggregateSnapshot snapshot = AggregateSnapshot.empty();
        long batches = 0L;
        long events = 0L;
        long errors = 0L;
        boolean running = true;

        try {
            while (running && !stopRequested.get()) {
                PipelineMessage message = input.poll(pollTimeout);
                if (message == null) {
                    continue;
                }
                switch (message) {
                    case PoisonPill ignored -> {
                        running = false;
                        LOG.log(Level.FINE, "stage=aggregation received poison pill");
                    }
                    case EventBatch batch -> {
                        Map<String, AggregateResult> partial =
                                forkJoinPool.invoke(AggregationTask.forEvents(batch.events(), sequentialThreshold));
                        snapshot = snapshot.mergeAll(partial);
                        liveSnapshot.set(snapshot);
                        batches++;
                        events += batch.size();
                        metrics.recordAggregated(batch.size(), 1L);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errors++;
            metrics.recordError(NAME);
            LOG.log(Level.WARNING, "stage=aggregation interrupted after events={0}", events);
        }

        errors += publish(snapshot);

        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        StageStats stats = new StageStats(NAME, batches, events, events, 0L, errors, elapsed);
        LOG.info(stats.toLogLine());
        return new AggregationOutcome(stats, snapshot);
    }

    /**
     * Publishes and stores the final snapshot.
     *
     * <p>Sink and repository failures are counted rather than thrown: losing the
     * report is bad, but it must not make an otherwise correct run look like a
     * pipeline failure. The returned error count reaches the final report.
     */
    private long publish(AggregateSnapshot snapshot) {
        long errors = 0L;
        try {
            sink.publish(snapshot);
        } catch (RuntimeException e) {
            errors++;
            metrics.recordError(NAME);
            LOG.log(Level.SEVERE, "stage=aggregation sink failed", e);
        }
        try {
            repository.save(snapshot);
        } catch (RuntimeException e) {
            errors++;
            metrics.recordError(NAME);
            LOG.log(Level.SEVERE, "stage=aggregation repository save failed", e);
        }
        return errors;
    }
}
