package com.example.pipeline.application.stage;

import com.example.pipeline.application.port.EventGenerator;
import com.example.pipeline.application.port.MessageChannel;
import com.example.pipeline.application.port.MetricsRecorder;
import com.example.pipeline.application.port.RateLimiter;
import com.example.pipeline.domain.EventBatch;
import com.example.pipeline.domain.SensorEvent;
import com.example.pipeline.domain.StageStats;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stage 1 — generates events at a paced rate, groups them into batches and hands
 * them to the downstream channel.
 *
 * <p>Runs on a single thread. Two things end the run: the event budget or the time
 * limit is reached, or {@link #requestStop()} is called (the {@code Ctrl+C} path).
 * Either way the stage finishes by enqueueing exactly one poison pill per
 * downstream consumer, which is what makes the shutdown a <em>drain</em> rather
 * than a <em>cancel</em>.
 *
 * <p><strong>Backpressure:</strong> {@link MessageChannel#put} blocks while the
 * channel is full, so a slow filter or aggregation stage throttles generation
 * automatically. Nothing is ever dropped to keep up.
 */
public final class ProducerStage {

    /** Stage name used in stats, metrics and log lines. */
    public static final String NAME = "producer";

    private static final Logger LOG = Logger.getLogger(ProducerStage.class.getName());

    private final EventGenerator generator;
    private final RateLimiter rateLimiter;
    private final MessageChannel output;
    private final MetricsRecorder metrics;
    private final int batchSize;
    private final long eventBudget;
    private final Duration maxDuration;
    private final int poisonPillCount;
    private final Executor executor;
    private final AtomicBoolean stopRequested = new AtomicBoolean();

    /**
     * @param generator       event source
     * @param rateLimiter     pacing; use {@link RateLimiter#unlimited()} to saturate
     * @param output          downstream channel (queue #1)
     * @param metrics         counters
     * @param batchSize       events per queue message, {@code >= 1}
     * @param eventBudget     total events to generate; {@code 0} means unlimited
     * @param maxDuration     wall-clock limit; {@link Duration#ZERO} means unlimited
     * @param poisonPillCount pills to emit at the end — must equal the number of
     *                        downstream consumer threads, or some will never wake up
     * @param executor        where the single producer task runs
     */
    public ProducerStage(EventGenerator generator, RateLimiter rateLimiter, MessageChannel output,
                         MetricsRecorder metrics, int batchSize, long eventBudget,
                         Duration maxDuration, int poisonPillCount, Executor executor) {
        this.generator = Objects.requireNonNull(generator, "generator");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.output = Objects.requireNonNull(output, "output");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.maxDuration = Objects.requireNonNull(maxDuration, "maxDuration");
        this.executor = Objects.requireNonNull(executor, "executor");
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be >= 1 but was " + batchSize);
        }
        if (eventBudget < 0) {
            throw new IllegalArgumentException("eventBudget must be >= 0 but was " + eventBudget);
        }
        if (poisonPillCount < 0) {
            throw new IllegalArgumentException("poisonPillCount must be >= 0 but was " + poisonPillCount);
        }
        if (eventBudget == 0 && maxDuration.isZero()) {
            throw new IllegalArgumentException("either eventBudget or maxDuration must be set, "
                    + "otherwise the producer would never stop on its own");
        }
        this.batchSize = batchSize;
        this.eventBudget = eventBudget;
        this.poisonPillCount = poisonPillCount;
    }

    /**
     * Starts the producer.
     *
     * @return a future completing with this stage's stats, or completing
     *         exceptionally if generation failed
     */
    public CompletableFuture<StageStats> startAsync() {
        return CompletableFuture.supplyAsync(this::run, executor);
    }

    /**
     * Asks the producer to stop after the current batch and emit its pills.
     *
     * <p>Cooperative and idempotent; safe to call from a shutdown hook thread.
     */
    public void requestStop() {
        stopRequested.set(true);
    }

    /** {@code true} once {@link #requestStop()} has been called. */
    public boolean isStopRequested() {
        return stopRequested.get();
    }

    /** Package-private so tests can run the stage body synchronously. */
    StageStats run() {
        long startNanos = System.nanoTime();
        long deadlineNanos = maxDuration.isZero() ? Long.MAX_VALUE : startNanos + maxDuration.toNanos();
        List<SensorEvent> buffer = new ArrayList<>(batchSize);
        long sequence = 0L;
        long batches = 0L;
        long errors = 0L;
        boolean interrupted = false;

        try {
            while (!stopRequested.get() && !budgetExhausted(sequence) && System.nanoTime() < deadlineNanos) {
                int size = nextBatchSize(sequence);
                // Acquire per batch, not per event: a per-event sleep is dominated by
                // OS timer granularity (10-15 ms on Windows) and caps the rate at ~100/s.
                rateLimiter.acquire(size);
                for (int i = 0; i < size; i++) {
                    buffer.add(generator.generate(sequence++));
                }
                // EventBatch copies the list, so reusing the buffer is safe.
                output.put(new EventBatch(batches, buffer));
                metrics.recordProduced(buffer.size(), 1L);
                batches++;
                buffer.clear();
            }
        } catch (InterruptedException e) {
            // Never swallow: restore the flag so the pool's shutdown logic still works.
            Thread.currentThread().interrupt();
            interrupted = true;
            LOG.log(Level.WARNING, "stage=producer interrupted after events={0}", sequence);
        }

        if (interrupted) {
            // Forcibly cancelled: a blocking put would fail immediately anyway.
            // Consumers exit via their poll timeout plus the interrupt from shutdownNow().
            errors++;
            metrics.recordError(NAME);
        } else if (!emitPoisonPills()) {
            errors++;
        }

        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        StageStats stats = new StageStats(NAME, batches, sequence, sequence, 0L, errors, elapsed);
        LOG.info(stats.toLogLine());
        return stats;
    }

    private boolean budgetExhausted(long produced) {
        return eventBudget > 0 && produced >= eventBudget;
    }

    private int nextBatchSize(long produced) {
        if (eventBudget == 0) {
            return batchSize;
        }
        long remaining = eventBudget - produced;
        return (int) Math.min(batchSize, remaining);
    }

    private boolean emitPoisonPills() {
        try {
            output.putPoisonPills(poisonPillCount);
            LOG.log(Level.FINE, "stage=producer pills={0} queue={1}",
                    new Object[] {poisonPillCount, output.name()});
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            metrics.recordError(NAME);
            LOG.log(Level.SEVERE, "stage=producer interrupted while emitting poison pills; "
                    + "consumers will exit on their poll timeout instead");
            return false;
        }
    }
}
