package com.example.pipeline.application.stage;

import com.example.pipeline.application.port.EventPredicate;
import com.example.pipeline.application.port.MessageChannel;
import com.example.pipeline.application.port.MetricsRecorder;
import com.example.pipeline.domain.EventBatch;
import com.example.pipeline.domain.PipelineMessage;
import com.example.pipeline.domain.PoisonPill;
import com.example.pipeline.domain.SensorEvent;
import com.example.pipeline.domain.StageStats;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stage 2 — a fixed pool of consumer threads applying a predicate to every event.
 *
 * <p>Each consumer runs the same loop: take a batch, keep the events that satisfy
 * the predicate, forward the survivors as a new batch. Rejected events are
 * <em>counted</em>, never silently dropped — that count is half of the end-of-run
 * reconciliation {@code produced == passed + rejected}.
 *
 * <p><strong>Poison pill handling</strong> (the subtle part):
 * <ul>
 *   <li>Each consumer exits on the first pill it sees and never re-enqueues it —
 *       re-offering a pill onto a full upstream queue is an immediate deadlock.</li>
 *   <li>The producer therefore emits exactly one pill per consumer.</li>
 *   <li>Only the <em>last</em> consumer to finish (an {@link AtomicInteger}
 *       countdown) forwards a single pill downstream. One pill per consumer would
 *       stop the aggregator at the first, discarding in-flight batches.</li>
 * </ul>
 */
public final class FilterStage {

    /** Stage name used in stats, metrics and log lines. */
    public static final String NAME = "filter";

    private static final Logger LOG = Logger.getLogger(FilterStage.class.getName());

    private final MessageChannel input;
    private final MessageChannel output;
    private final EventPredicate predicate;
    private final MetricsRecorder metrics;
    private final Executor consumerExecutor;
    private final int consumerCount;
    private final Duration pollTimeout;
    private final AtomicBoolean stopRequested = new AtomicBoolean();

    /**
     * @param input            upstream channel (queue #1)
     * @param output           downstream channel (queue #2)
     * @param predicate        thread-safe, non-blocking filter rule
     * @param metrics          counters
     * @param consumerExecutor pool with at least {@code consumerCount} threads
     * @param consumerCount    number of consumer tasks, {@code >= 1}
     * @param pollTimeout      how long a consumer waits before re-checking the stop flag
     */
    public FilterStage(MessageChannel input, MessageChannel output, EventPredicate predicate,
                       MetricsRecorder metrics, Executor consumerExecutor, int consumerCount,
                       Duration pollTimeout) {
        this.input = Objects.requireNonNull(input, "input");
        this.output = Objects.requireNonNull(output, "output");
        this.predicate = Objects.requireNonNull(predicate, "predicate");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.consumerExecutor = Objects.requireNonNull(consumerExecutor, "consumerExecutor");
        this.pollTimeout = Objects.requireNonNull(pollTimeout, "pollTimeout");
        if (consumerCount < 1) {
            throw new IllegalArgumentException("consumerCount must be >= 1 but was " + consumerCount);
        }
        if (pollTimeout.isZero() || pollTimeout.isNegative()) {
            throw new IllegalArgumentException("pollTimeout must be positive but was " + pollTimeout);
        }
        this.consumerCount = consumerCount;
    }

    /**
     * Starts every consumer.
     *
     * @return a future completing with the summed stats of all consumers; it
     *         completes exceptionally if any consumer threw
     */
    public CompletableFuture<StageStats> startAsync() {
        AtomicInteger remaining = new AtomicInteger(consumerCount);
        List<CompletableFuture<StageStats>> consumers = new ArrayList<>(consumerCount);
        for (int i = 0; i < consumerCount; i++) {
            int consumerId = i;
            consumers.add(CompletableFuture.supplyAsync(() -> consume(consumerId, remaining), consumerExecutor));
        }
        return CompletableFuture.allOf(consumers.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> consumers.stream()
                        .map(CompletableFuture::join)
                        .reduce(StageStats.empty(NAME), StageStats::plus));
    }

    /** Cooperative stop; consumers notice within one {@code pollTimeout}. */
    public void requestStop() {
        stopRequested.set(true);
    }

    /** How many consumer tasks this stage runs. */
    public int consumerCount() {
        return consumerCount;
    }

    /** Package-private so tests can drive one consumer synchronously. */
    StageStats consume(int consumerId, AtomicInteger remaining) {
        long startNanos = System.nanoTime();
        long batches = 0L;
        long eventsIn = 0L;
        long eventsOut = 0L;
        long rejected = 0L;
        long errors = 0L;
        boolean running = true;

        try {
            while (running && !stopRequested.get()) {
                PipelineMessage message = input.poll(pollTimeout);
                if (message == null) {
                    // Timed out: loop round to re-check the stop flag. Using take()
                    // here would make a stalled upstream unstoppable.
                    continue;
                }
                // No default branch: the sealed hierarchy makes this exhaustive, and a
                // default would silence the compiler if a third message type appeared.
                switch (message) {
                    case PoisonPill ignored -> {
                        running = false;
                        LOG.log(Level.FINE, "stage=filter consumer={0} received poison pill", consumerId);
                    }
                    case EventBatch batch -> {
                        batches++;
                        eventsIn += batch.size();
                        List<SensorEvent> passed = new ArrayList<>(batch.size());
                        for (SensorEvent event : batch.events()) {
                            if (predicate.test(event)) {
                                passed.add(event);
                            }
                        }
                        long rejectedHere = batch.size() - passed.size();
                        rejected += rejectedHere;
                        eventsOut += passed.size();
                        metrics.recordFiltered(passed.size(), rejectedHere);
                        if (!passed.isEmpty()) {
                            output.put(new EventBatch(batch.batchId(), passed));
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errors++;
            metrics.recordError(NAME);
            LOG.log(Level.WARNING, "stage=filter consumer={0} interrupted", consumerId);
        } finally {
            forwardPillIfLast(remaining, consumerId);
        }

        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        StageStats stats = new StageStats(NAME, batches, eventsIn, eventsOut, rejected, errors, elapsed);
        LOG.log(Level.FINE, "consumer={0} {1}", new Object[] {consumerId, stats.toLogLine()});
        return stats;
    }

    /**
     * Forwards exactly one pill downstream, and only from the consumer that finishes last.
     *
     * <p>In a {@code finally} block on purpose: even a consumer that dies on an
     * unexpected exception must not leave the aggregator waiting forever.
     */
    private void forwardPillIfLast(AtomicInteger remaining, int consumerId) {
        if (remaining.decrementAndGet() != 0) {
            return;
        }
        try {
            output.putPoisonPills(1);
            LOG.log(Level.FINE, "stage=filter consumer={0} forwarded poison pill downstream", consumerId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            metrics.recordError(NAME);
            LOG.severe("stage=filter interrupted before forwarding the downstream poison pill; "
                    + "aggregation will exit on its poll timeout");
        }
    }
}
