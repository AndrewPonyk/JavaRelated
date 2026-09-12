package com.example.pipeline.domain;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * What one stage did: the value each stage's {@code CompletableFuture} completes with.
 *
 * <p>Returning stats from the stage (rather than only writing to shared counters)
 * means a stage's outcome is available even if the metrics recorder is a no-op,
 * and it makes stages testable without any observability wiring.
 *
 * @param stage           stage name, e.g. {@code producer}
 * @param batches         messages handled
 * @param eventsIn        events received (produced, for stage 1)
 * @param eventsOut       events forwarded downstream
 * @param eventsRejected  events dropped by a predicate (counted, never silent)
 * @param errors          per-event or per-batch failures that did not abort the stage
 * @param elapsed         wall-clock time the stage was running
 */
public record StageStats(String stage, long batches, long eventsIn, long eventsOut,
                         long eventsRejected, long errors, Duration elapsed) {

    /** Rejects negative tallies — a negative count means a counting bug, not a data point. */
    public StageStats {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(elapsed, "elapsed");
        if (batches < 0 || eventsIn < 0 || eventsOut < 0 || eventsRejected < 0 || errors < 0) {
            throw new IllegalArgumentException("stage tallies must be >= 0: " + stage);
        }
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("elapsed must not be negative: " + elapsed);
        }
    }

    /** All-zero stats for {@code stage}. */
    public static StageStats empty(String stage) {
        return new StageStats(stage, 0L, 0L, 0L, 0L, 0L, Duration.ZERO);
    }

    /**
     * Sums two stats objects; used to fold the per-consumer stats of the filter
     * stage into one. Elapsed time takes the {@code max}, not the sum — consumers
     * run concurrently, so adding their durations would report a wall-clock time
     * that never happened.
     */
    public StageStats plus(StageStats other) {
        Objects.requireNonNull(other, "other");
        Duration longest = elapsed.compareTo(other.elapsed) >= 0 ? elapsed : other.elapsed;
        return new StageStats(stage, batches + other.batches, eventsIn + other.eventsIn,
                eventsOut + other.eventsOut, eventsRejected + other.eventsRejected,
                errors + other.errors, longest);
    }

    /** Events per second based on {@link #eventsIn}; {@code 0} when no time has elapsed. */
    public double throughputPerSecond() {
        long nanos = elapsed.toNanos();
        return nanos <= 0L ? 0.0 : eventsIn * 1_000_000_000.0 / nanos;
    }

    /** Single-line, key=value rendering: greppable without a log pipeline. */
    public String toLogLine() {
        return String.format(Locale.ROOT,
                "stage=%s batches=%d in=%d out=%d rejected=%d errors=%d elapsed=%dms rate=%.0f/s",
                stage, batches, eventsIn, eventsOut, eventsRejected, errors,
                elapsed.toMillis(), throughputPerSecond());
    }
}
