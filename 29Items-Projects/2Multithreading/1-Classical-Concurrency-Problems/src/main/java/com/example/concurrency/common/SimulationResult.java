package com.example.concurrency.common;

import java.time.Duration;
import java.util.Objects;

/** Immutable summary returned by a bounded simulation. */
public record SimulationResult(
        String scenario,
        int participants,
        long completedOperations,
        Duration elapsed,
        boolean completed) {

    public SimulationResult {
        scenario = Objects.requireNonNull(scenario, "scenario");
        elapsed = Objects.requireNonNull(elapsed, "elapsed");
        if (scenario.isBlank()) {
            throw new IllegalArgumentException("scenario must not be blank");
        }
        if (participants < 0 || completedOperations < 0 || elapsed.isNegative()) {
            throw new IllegalArgumentException("counts and duration must be non-negative");
        }
    }
}
