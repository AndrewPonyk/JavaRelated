package com.example.pipeline.application;

import com.example.pipeline.application.port.MetricsRecorder;
import com.example.pipeline.application.port.StageLifecycle;
import com.example.pipeline.application.stage.AggregationOutcome;
import com.example.pipeline.application.stage.AggregationStage;
import com.example.pipeline.application.stage.FilterStage;
import com.example.pipeline.application.stage.ProducerStage;
import com.example.pipeline.domain.AggregateSnapshot;
import com.example.pipeline.domain.MetricsSnapshot;
import com.example.pipeline.domain.StageStats;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Starts the three stages, waits for them to drain, and turns the result into a
 * {@link PipelineReport}.
 *
 * <p><strong>Why {@code CompletableFuture} here and not inside the stages:</strong>
 * the interesting composition is at the <em>lifecycle</em> level — "all three
 * finished", "one blew up, so stop the others", "the drain overran, escalate". A
 * future per event would add an allocation and a callback per event and buy nothing;
 * the queues already provide the handoff.
 *
 * <p><strong>Shutdown is a drain, not a cancel.</strong> The happy path is:
 * producer finishes → pills traverse queue #1 → consumers exit and forward one pill
 * → aggregator exits → all three futures complete → pools are shut down with nothing
 * left running. {@link StageLifecycle#forceShutdown()} is only reached when that
 * drain overruns the timeout, and a run that needed it is reported as failed even if
 * the numbers happen to look fine.
 *
 * <p><strong>There is deliberately no cap on how long a run may take.</strong> How long
 * a run lasts is decided by {@code pipeline.event.count} and
 * {@code pipeline.duration.seconds} — the producer's own bounds — or by an operator
 * pressing {@code Ctrl+C}; a five-minute run of fifty million events is not a fault.
 * What this class watches for instead is <em>loss of liveness</em>: a pipeline that has
 * stopped moving events for {@code shutdownTimeout} is wedged and is reported as such.
 * The distinction matters because the two are trivially confused, and confusing them
 * means a healthy pipeline is killed mid-flight for the crime of having work to do —
 * with a report that reads {@code FAILED} directly above {@code reconciled=true}.
 *
 * <p>Not thread-safe and not reusable: construct one per run.
 */
public final class PipelineOrchestrator {

    private static final Logger LOG = Logger.getLogger(PipelineOrchestrator.class.getName());

    /**
     * How often the drain wait wakes up to compare the progress counters.
     *
     * <p>Short enough that the stall verdict is punctual, long enough that the polling
     * itself is free: a snapshot copies one small map, ten times a second, on the thread
     * that would otherwise just be blocked in {@code get}.
     */
    private static final Duration PROGRESS_POLL = Duration.ofMillis(100);

    private final ProducerStage producer;
    private final FilterStage filter;
    private final AggregationStage aggregation;
    private final StageLifecycle lifecycle;
    private final MetricsRecorder metrics;
    private final Duration shutdownTimeout;

    /**
     * @param producer        stage 1
     * @param filter          stage 2
     * @param aggregation     stage 3
     * @param lifecycle       owner of the thread pools
     * @param metrics         counters, read once after all stages terminate
     * @param shutdownTimeout how long the drain may take before escalation
     */
    public PipelineOrchestrator(ProducerStage producer, FilterStage filter, AggregationStage aggregation,
                                StageLifecycle lifecycle, MetricsRecorder metrics, Duration shutdownTimeout) {
        this.producer = Objects.requireNonNull(producer, "producer");
        this.filter = Objects.requireNonNull(filter, "filter");
        this.aggregation = Objects.requireNonNull(aggregation, "aggregation");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.shutdownTimeout = Objects.requireNonNull(shutdownTimeout, "shutdownTimeout");
        if (shutdownTimeout.isZero() || shutdownTimeout.isNegative()) {
            throw new IllegalArgumentException("shutdownTimeout must be positive but was " + shutdownTimeout);
        }
    }

    /**
     * Runs the pipeline to completion.
     *
     * <p>Never throws for a pipeline-level problem: a stage failure, a timeout or an
     * interrupt all come back as an unsuccessful report, because the caller needs the
     * partial numbers to diagnose the run. Only a programming error (an unchecked
     * exception from the reporting code itself) would propagate.
     *
     * @return the report; {@link PipelineReport#success()} is the single verdict
     */
    public PipelineReport run() {
        long startNanos = System.nanoTime();

        CompletableFuture<StageStats> producerFuture = producer.startAsync();
        CompletableFuture<StageStats> filterFuture = filter.startAsync();
        CompletableFuture<AggregationOutcome> aggregationFuture = aggregation.startAsync();
        CompletableFuture<Void> all = CompletableFuture.allOf(producerFuture, filterFuture, aggregationFuture);

        String message;
        boolean drained;
        try {
            awaitLiveStages(all);
            message = "all stages drained";
            drained = true;
        } catch (TimeoutException e) {
            message = "pipeline stalled: no progress for " + shutdownTimeout.toMillis()
                    + " ms; stages were stopped";
            drained = false;
            LOG.log(Level.SEVERE, "pipeline stalled: no counter moved for {0} ms", shutdownTimeout.toMillis());
            stopEveryStage();
        } catch (ExecutionException e) {
            message = "stage failed: " + rootCauseMessage(e);
            drained = false;
            LOG.log(Level.SEVERE, "pipeline stage failed", e.getCause());
            stopEveryStage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            message = "interrupted while waiting for the drain";
            drained = false;
            LOG.warning("pipeline interrupted while waiting for the drain");
            stopEveryStage();
        }

        // Give the stages one more timeout window to notice the stop flag, then escalate.
        boolean poolsTerminated = shutdownPools();

        StageStats producerStats = statsOrEmpty(producerFuture, ProducerStage.NAME);
        StageStats filterStats = statsOrEmpty(filterFuture, FilterStage.NAME);
        AggregationOutcome aggregationOutcome = outcomeOrEmpty(aggregationFuture);
        // Read the counters only now: every writer has terminated, so the snapshot is
        // consistent and reconciles() is meaningful.
        MetricsSnapshot metricsSnapshot = metrics.snapshot();
        AggregateSnapshot snapshot = aggregationOutcome.snapshot();

        long errors = producerStats.errors() + filterStats.errors() + aggregationOutcome.stats().errors();
        boolean success = drained && poolsTerminated && errors == 0L && metricsSnapshot.reconciles();
        if (success) {
            message = "all stages drained; " + metricsSnapshot.eventsProduced() + " events reconciled";
        } else if (drained && !metricsSnapshot.reconciles()) {
            message = "counters do not reconcile: " + metricsSnapshot.lostEvents() + " events unaccounted for";
        } else if (drained && errors > 0L) {
            message = "drained with " + errors + " stage error(s)";
        } else if (drained && !poolsTerminated) {
            message = "stages drained but pools did not terminate within the timeout";
        }

        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        PipelineReport report = new PipelineReport(success, message, producerStats, filterStats,
                aggregationOutcome.stats(), snapshot, metricsSnapshot, elapsed);
        LOG.info("pipeline finished success=" + success + " " + metricsSnapshot.toLogLine());
        return report;
    }

    /**
     * Waits for all three stages, for as long as the pipeline keeps moving events.
     *
     * <p>The wait ends when the stages complete, when one of them fails, or when
     * {@code shutdownTimeout} passes with <em>no counter having moved at all</em> — which
     * is the observable signature of every way this graph can wedge: a consumer stuck
     * inside the predicate, a pill lost so a stage polls an empty queue forever, or a
     * producer parked in {@code put()} against a queue nobody drains any more.
     *
     * <p>Summing the counters is a sound stall test precisely because each one only ever
     * increases: an unchanged sum means no single counter moved. Queue-blocked nanoseconds
     * are excluded on purpose — they keep climbing while the producer is parked, so
     * including them would mask the one case most worth catching.
     *
     * @throws TimeoutException if the pipeline stops making progress for the whole budget
     */
    private void awaitLiveStages(CompletableFuture<Void> all)
            throws InterruptedException, ExecutionException, TimeoutException {
        long stallBudgetNanos = shutdownTimeout.toNanos();
        long lastProgressNanos = System.nanoTime();
        long progress = progressCount();
        while (true) {
            try {
                all.get(PROGRESS_POLL.toMillis(), TimeUnit.MILLISECONDS);
                return;
            } catch (TimeoutException stillRunning) {
                long current = progressCount();
                if (current != progress) {
                    progress = current;
                    lastProgressNanos = System.nanoTime();
                } else if (System.nanoTime() - lastProgressNanos >= stallBudgetNanos) {
                    throw stillRunning;
                }
            }
        }
    }

    /** Monotonic sum of every event and batch counter; see {@link #awaitLiveStages}. */
    private long progressCount() {
        MetricsSnapshot snapshot = metrics.snapshot();
        return snapshot.eventsProduced() + snapshot.batchesProduced() + snapshot.eventsFiltered()
                + snapshot.eventsAggregated() + snapshot.batchesAggregated() + snapshot.errors();
    }

    /**
     * Asks the pipeline to wind down, losing nothing that has already been produced.
     *
     * <p>The {@code Ctrl+C} entry point: safe from any thread and idempotent, so a
     * shutdown hook can call it while {@link #run()} is still blocked in {@code get}.
     *
     * <p><strong>Only the producer is stopped, deliberately.</strong> It is the head of
     * the graph, so stopping it is enough to end the run — on its way out it emits one
     * poison pill per consumer, and those pills cascade through the rest of the pipeline
     * behind the data that is already queued. Setting the stop flag on all three stages
     * instead would let the consumers exit while queue #1 still held batches, which
     * loses events <em>and</em> wedges the producer in {@code put()} against a full queue
     * that nobody is draining any more — turning {@code Ctrl+C} into a drain timeout and
     * a forced shutdown. {@link #stopEveryStage()} is that harsher stop, and it is
     * reserved for the case where a drain has already failed.
     */
    public void requestStopAll() {
        producer.requestStop();
    }

    /**
     * Sets the stop flag on all three stages, abandoning whatever is still queued.
     *
     * <p>Only for use once the drain has already overrun or a stage has already failed:
     * at that point no pill is coming, and the aim is merely to get the consumer loops
     * out of {@code poll} before {@link StageLifecycle#forceShutdown()} interrupts them.
     * Events still in the queues are lost, which is why every caller also reports the
     * run as failed.
     */
    private void stopEveryStage() {
        producer.requestStop();
        filter.requestStop();
        aggregation.requestStop();
    }

    /**
     * Drains the pools, escalating to interrupts if the drain overruns.
     *
     * @return {@code true} if every pool terminated without needing the escalation
     */
    private boolean shutdownPools() {
        try {
            if (lifecycle.shutdownAndAwait(shutdownTimeout)) {
                return true;
            }
            LOG.severe("pools did not terminate within the timeout; interrupting remaining tasks");
            lifecycle.forceShutdown();
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lifecycle.forceShutdown();
            return false;
        }
    }

    /**
     * Stats of a stage that may not have completed.
     *
     * <p>{@code getNow} rather than {@code join}: by this point the pools are down, so
     * a stage that has not completed never will, and blocking on it would hang the
     * report. An incomplete stage contributes zeros — the metrics snapshot is what
     * exposes the shortfall.
     */
    private static StageStats statsOrEmpty(CompletableFuture<StageStats> future, String stage) {
        try {
            return future.getNow(StageStats.empty(stage));
        } catch (CompletionException | CancellationException e) {
            LOG.log(Level.FINE, "stage {0} completed exceptionally; reporting zeros", stage);
            return StageStats.empty(stage);
        }
    }

    /** Aggregation outcome of a stage that may not have completed; see {@link #statsOrEmpty}. */
    private static AggregationOutcome outcomeOrEmpty(CompletableFuture<AggregationOutcome> future) {
        try {
            return future.getNow(AggregationOutcome.empty(AggregationStage.NAME));
        } catch (CompletionException | CancellationException e) {
            LOG.log(Level.FINE, "aggregation completed exceptionally; reporting zeros");
            return AggregationOutcome.empty(AggregationStage.NAME);
        }
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String detail = cause.getMessage();
        return cause.getClass().getSimpleName() + (detail == null ? "" : ": " + detail);
    }
}
