package com.example.pipeline.domain;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable point-in-time view of every per-sensor aggregate.
 *
 * <p>The aggregation stage keeps one snapshot as its running state and replaces it
 * with a merged copy per batch. Copy-on-merge is affordable here because the map
 * has one entry per <em>sensor</em> (tens), not per event (millions), and it buys
 * a value that can be published to a sink or a dashboard thread with no locking.
 *
 * @param results sensor id to aggregate; copied defensively on construction
 */
public record AggregateSnapshot(Map<String, AggregateResult> results) {

    /** Copies the map defensively; {@code Map.copyOf} also rejects null keys and values. */
    public AggregateSnapshot {
        Objects.requireNonNull(results, "results");
        results = Map.copyOf(results);
    }

    /** Empty snapshot — the starting state of the aggregation stage. */
    public static AggregateSnapshot empty() {
        return new AggregateSnapshot(Map.of());
    }

    /**
     * Returns a new snapshot with {@code partial} folded in, merging per sensor.
     *
     * @param partial typically the result of one {@code AggregationTask}
     */
    public AggregateSnapshot mergeAll(Map<String, AggregateResult> partial) {
        Objects.requireNonNull(partial, "partial");
        if (partial.isEmpty()) {
            return this;
        }
        Map<String, AggregateResult> merged = new HashMap<>(results);
        partial.forEach((sensorId, result) -> merged.merge(sensorId, result, AggregateResult::merge));
        return new AggregateSnapshot(merged);
    }

    /** Combines two snapshots; used when several aggregators run in parallel. */
    public AggregateSnapshot mergeAll(AggregateSnapshot other) {
        Objects.requireNonNull(other, "other");
        return mergeAll(other.results);
    }

    /** Total number of aggregated events across all sensors. */
    public long totalCount() {
        long total = 0L;
        for (AggregateResult result : results.values()) {
            total += result.count();
        }
        return total;
    }

    /** How many distinct sensors reported at least one event. */
    public int sensorCount() {
        return results.size();
    }

    /** {@code true} when nothing has been aggregated yet. */
    public boolean isEmpty() {
        return results.isEmpty();
    }

    /** Aggregates ordered by sensor id — stable ordering for reports and assertions. */
    public List<AggregateResult> sortedBySensorId() {
        return results.values().stream()
                .sorted(Comparator.comparing(AggregateResult::sensorId))
                .toList();
    }
}
