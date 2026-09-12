package com.example.trading.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

public record SimulationReport(
        int traderCount,
        int orderCount,
        int tradeCount,
        int activeOrderCount,
        BigDecimal aggregateProfit,
        Duration elapsed,
        Duration p95OrderLatency) {

    public SimulationReport {
        if (traderCount < 0 || orderCount < 0 || tradeCount < 0 || activeOrderCount < 0) {
            throw new IllegalArgumentException("report counts must not be negative");
        }
        if (activeOrderCount > orderCount) {
            throw new IllegalArgumentException("activeOrderCount must not exceed orderCount");
        }
        aggregateProfit = Objects.requireNonNull(aggregateProfit, "aggregateProfit");
        elapsed = Objects.requireNonNull(elapsed, "elapsed");
        p95OrderLatency = Objects.requireNonNull(p95OrderLatency, "p95OrderLatency");
        if (elapsed.isNegative() || p95OrderLatency.isNegative()) {
            throw new IllegalArgumentException("report durations must not be negative");
        }
    }
}
