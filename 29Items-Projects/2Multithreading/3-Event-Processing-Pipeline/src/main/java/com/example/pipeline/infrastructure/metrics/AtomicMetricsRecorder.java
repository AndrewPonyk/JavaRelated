package com.example.pipeline.infrastructure.metrics;

import com.example.pipeline.application.port.MetricsRecorder;
import com.example.pipeline.domain.MetricsSnapshot;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Counter set backed by {@link LongAdder}s.
 *
 * <p><strong>{@code LongAdder}, not {@code AtomicLong}.</strong> These counters are
 * written by every stage thread on every batch and read roughly twice per run (the
 * dashboard tick and the final report). {@code AtomicLong.incrementAndGet} is a CAS on
 * one cache line, so under contention the threads spend their time invalidating each
 * other's caches. {@code LongAdder} keeps a per-thread cell and only sums on read —
 * exactly the right trade for write-heavy, read-rare data.
 *
 * <p><strong>A snapshot is not an atomic instant.</strong> {@link #snapshot()} reads the
 * adders one at a time, so a mid-run snapshot can show {@code produced} lagging
 * {@code passed + rejected}. That is inherent and not worth a lock: locking the hot path
 * to make a display line self-consistent would cost more than the display is worth.
 * {@link MetricsSnapshot#reconciles()} is therefore only meaningful once every stage has
 * terminated — which is exactly when the orchestrator calls it.
 *
 * <p>Per-queue blocked time lives in a {@code ConcurrentHashMap<String, LongAdder>}:
 * queue names are created once at startup, so {@code computeIfAbsent} never contends in
 * steady state.
 */
public final class AtomicMetricsRecorder implements MetricsRecorder {

    private final LongAdder eventsProduced = new LongAdder();
    private final LongAdder batchesProduced = new LongAdder();
    private final LongAdder eventsPassed = new LongAdder();
    private final LongAdder eventsRejected = new LongAdder();
    private final LongAdder eventsAggregated = new LongAdder();
    private final LongAdder batchesAggregated = new LongAdder();
    private final LongAdder errors = new LongAdder();
    private final Map<String, LongAdder> queueBlockedNanos = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> errorsByStage = new ConcurrentHashMap<>();

    @Override
    public void recordProduced(long events, long batches) {
        eventsProduced.add(events);
        batchesProduced.add(batches);
    }

    @Override
    public void recordFiltered(long passed, long rejected) {
        eventsPassed.add(passed);
        eventsRejected.add(rejected);
    }

    @Override
    public void recordAggregated(long events, long batches) {
        eventsAggregated.add(events);
        batchesAggregated.add(batches);
    }

    @Override
    public void recordError(String stage) {
        Objects.requireNonNull(stage, "stage");
        errors.increment();
        errorsByStage.computeIfAbsent(stage, ignored -> new LongAdder()).increment();
    }

    @Override
    public void recordQueueBlocked(String queueName, long nanos) {
        Objects.requireNonNull(queueName, "queueName");
        if (nanos <= 0L) {
            return;
        }
        queueBlockedNanos.computeIfAbsent(queueName, ignored -> new LongAdder()).add(nanos);
    }

    @Override
    public MetricsSnapshot snapshot() {
        Map<String, Long> blocked = new HashMap<>();
        queueBlockedNanos.forEach((name, adder) -> blocked.put(name, adder.sum()));
        return new MetricsSnapshot(eventsProduced.sum(), batchesProduced.sum(), eventsPassed.sum(),
                eventsRejected.sum(), eventsAggregated.sum(), batchesAggregated.sum(), errors.sum(), blocked);
    }

    /**
     * Errors attributed to one stage.
     *
     * <p>Beyond the port because only diagnostics need the breakdown; the report only
     * needs the total.
     */
    public long errorsFor(String stage) {
        LongAdder adder = errorsByStage.get(stage);
        return adder == null ? 0L : adder.sum();
    }

    @Override
    public void reset() {
        eventsProduced.reset();
        batchesProduced.reset();
        eventsPassed.reset();
        eventsRejected.reset();
        eventsAggregated.reset();
        batchesAggregated.reset();
        errors.reset();
        queueBlockedNanos.clear();
        errorsByStage.clear();
    }

    @Override
    public String toString() {
        return snapshot().toLogLine();
    }
}
