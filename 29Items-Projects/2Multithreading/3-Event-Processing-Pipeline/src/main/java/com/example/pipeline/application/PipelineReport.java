package com.example.pipeline.application;

import com.example.pipeline.domain.AggregateResult;
import com.example.pipeline.domain.AggregateSnapshot;
import com.example.pipeline.domain.MetricsSnapshot;
import com.example.pipeline.domain.StageStats;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * Everything one pipeline run produced: the outcome, the per-stage tallies, the
 * aggregates and the counters.
 *
 * <p>The orchestrator returns this instead of printing and calling
 * {@code System.exit} itself. That split is what makes the whole pipeline testable:
 * a test runs the orchestrator and asserts on a value, while {@code main} is a thin
 * shell that renders the value and maps {@link #exitCode()} onto a process status.
 *
 * @param success   {@code true} only if every stage finished and no events were lost
 * @param message   human-readable summary of why the run ended the way it did
 * @param producer  stage 1 tallies
 * @param filter    stage 2 tallies, summed across consumers
 * @param aggregation stage 3 tallies
 * @param snapshot  final per-sensor aggregates
 * @param metrics   counter snapshot taken after every stage terminated
 * @param elapsed   wall-clock time of the whole run
 */
public record PipelineReport(boolean success, String message, StageStats producer, StageStats filter,
                            StageStats aggregation, AggregateSnapshot snapshot, MetricsSnapshot metrics,
                            Duration elapsed) {

    /** Exit code for a clean run. */
    public static final int EXIT_OK = 0;

    /** Exit code for a run that completed but lost events or hit stage failures. */
    public static final int EXIT_FAILURE = 1;

    /**
     * Exit code for invalid configuration — nothing was started.
     *
     * <p>Distinct from {@link #EXIT_FAILURE} on purpose: a CI job can retry a flaky run but
     * must never retry a bad config, and the two are indistinguishable if both exit 1. No
     * report is ever produced with this code; it is emitted by {@code main} before the graph
     * is built, and lives here so the whole exit-code contract is stated in one place.
     */
    public static final int EXIT_CONFIG = 2;

    /** Exit code for a run interrupted after a clean drain (the {@code Ctrl+C} path). */
    public static final int EXIT_INTERRUPTED = 130;

    /** Rejects nulls; every component is present even for a failed run. */
    public PipelineReport {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(producer, "producer");
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(aggregation, "aggregation");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(elapsed, "elapsed");
    }

    /**
     * Report for a run that failed before any stage produced stats.
     *
     * @param message why the run failed
     * @param elapsed how long the attempt took
     */
    public static PipelineReport failed(String message, Duration elapsed) {
        return new PipelineReport(false, message, StageStats.empty("producer"), StageStats.empty("filter"),
                StageStats.empty("aggregation"), AggregateSnapshot.empty(), MetricsSnapshot.empty(), elapsed);
    }

    /** Total errors counted across all three stages. */
    public long totalErrors() {
        return producer.errors() + filter.errors() + aggregation.errors();
    }

    /**
     * Events produced but never accounted for by the filter stage.
     *
     * <p>Anything other than zero is a correctness bug, not a performance note.
     */
    public long lostEvents() {
        return metrics.lostEvents();
    }

    /**
     * Process exit status.
     *
     * <p>Distinguishing 130 from 1 matters for scripts: an operator pressing
     * {@code Ctrl+C} during a soak run should not look like a pipeline defect.
     */
    public int exitCode() {
        if (success) {
            return EXIT_OK;
        }
        return Thread.currentThread().isInterrupted() ? EXIT_INTERRUPTED : EXIT_FAILURE;
    }

    /** Multi-line, fixed-width report for the console. ASCII only, so Windows terminals render it. */
    public String describe() {
        StringBuilder out = new StringBuilder(512);
        out.append("=== Pipeline report ===").append(System.lineSeparator());
        out.append(success ? "result : OK" : "result : FAILED").append(System.lineSeparator());
        out.append("reason : ").append(message).append(System.lineSeparator());
        out.append(String.format(Locale.ROOT, "elapsed: %d ms%n", elapsed.toMillis()));
        out.append(producer.toLogLine()).append(System.lineSeparator());
        out.append(filter.toLogLine()).append(System.lineSeparator());
        out.append(aggregation.toLogLine()).append(System.lineSeparator());
        out.append(metrics.toLogLine()).append(System.lineSeparator());
        out.append(String.format(Locale.ROOT, "--- aggregates (%d sensors) ---%n", snapshot.sensorCount()));
        for (AggregateResult result : snapshot.sortedBySensorId()) {
            out.append(result.toDisplayRow()).append(System.lineSeparator());
        }
        return out.toString();
    }
}
