package com.example.pipeline.application.stage;

import com.example.pipeline.domain.AggregateSnapshot;
import com.example.pipeline.domain.StageStats;
import java.util.Objects;

/**
 * What the aggregation stage completes with: its tallies plus the final results.
 *
 * <p>A dedicated record rather than a naked {@code AggregateSnapshot} so the stage
 * can report throughput and error counts like the other two stages, without a
 * side-channel through the metrics recorder.
 *
 * @param stats    per-stage tallies
 * @param snapshot final per-sensor aggregates
 */
public record AggregationOutcome(StageStats stats, AggregateSnapshot snapshot) {

    /** Rejects nulls; both components are always present, even for an empty run. */
    public AggregationOutcome {
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(snapshot, "snapshot");
    }

    /** Outcome of a stage that aggregated nothing. */
    public static AggregationOutcome empty(String stage) {
        return new AggregationOutcome(StageStats.empty(stage), AggregateSnapshot.empty());
    }
}
