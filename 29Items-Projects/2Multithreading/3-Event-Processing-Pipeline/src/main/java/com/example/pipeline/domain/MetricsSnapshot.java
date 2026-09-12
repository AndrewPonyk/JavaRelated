package com.example.pipeline.domain;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Consistent-enough copy of the live counters, taken for display or for the final report.
 *
 * <p><strong>Read this before asserting on a mid-flight snapshot:</strong> the
 * counters are independent {@code LongAdder}s, so a snapshot is <em>not</em> an
 * atomic instant across all fields. Seeing {@code produced=1000, passed=400,
 * rejected=590} while the pipeline runs is normal and not a bug. Only reconcile
 * ({@link #reconciles()}) after every stage has completed.
 *
 * <p>Lives in {@code domain} rather than in the metrics adapter so the
 * {@code MetricsRecorder} port can return it without the application layer
 * depending on infrastructure.
 *
 * @param eventsProduced    events handed to queue #1
 * @param batchesProduced   batches handed to queue #1
 * @param eventsPassed      events that satisfied the filter predicate
 * @param eventsRejected    events the predicate rejected
 * @param eventsAggregated  events folded into the aggregate snapshot
 * @param batchesAggregated batches folded into the aggregate snapshot
 * @param errors            counted failures across all stages
 * @param queueBlockedNanos queue name to nanoseconds spent blocked in {@code put}
 */
public record MetricsSnapshot(long eventsProduced, long batchesProduced, long eventsPassed,
                              long eventsRejected, long eventsAggregated, long batchesAggregated,
                              long errors, Map<String, Long> queueBlockedNanos) {

    /** Copies the per-queue map defensively. */
    public MetricsSnapshot {
        Objects.requireNonNull(queueBlockedNanos, "queueBlockedNanos");
        queueBlockedNanos = Map.copyOf(queueBlockedNanos);
    }

    /** All-zero snapshot. */
    public static MetricsSnapshot empty() {
        return new MetricsSnapshot(0L, 0L, 0L, 0L, 0L, 0L, 0L, Map.of());
    }

    /** Events the filter stage has decided about, either way. */
    public long eventsFiltered() {
        return eventsPassed + eventsRejected;
    }

    /**
     * The end-of-run correctness check: every produced event was either passed or
     * rejected — none vanished in a queue, a pill race or a swallowed exception.
     *
     * <p>Only meaningful once all stages have terminated.
     */
    public boolean reconciles() {
        return eventsProduced == eventsFiltered();
    }

    /** Produced minus filtered — non-zero means events were lost. */
    public long lostEvents() {
        return eventsProduced - eventsFiltered();
    }

    /** Total time all producers spent blocked on full queues, across every queue. */
    public long totalBlockedNanos() {
        long total = 0L;
        for (Long nanos : queueBlockedNanos.values()) {
            total += nanos;
        }
        return total;
    }

    /**
     * Name of the queue with the highest blocked time, or {@code "none"}.
     *
     * <p>This is the bottleneck finder: the stage <em>downstream</em> of the most
     * blocked queue is the constraint.
     */
    public String mostBlockedQueue() {
        return queueBlockedNanos.entrySet().stream()
                .max(Comparator.comparingLong(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse("none");
    }

    /** Single-line, key=value rendering for logs. */
    public String toLogLine() {
        return String.format(Locale.ROOT,
                "produced=%d passed=%d rejected=%d aggregated=%d errors=%d blocked=%dms reconciled=%b",
                eventsProduced, eventsPassed, eventsRejected, eventsAggregated, errors,
                totalBlockedNanos() / 1_000_000L, reconciles());
    }
}
