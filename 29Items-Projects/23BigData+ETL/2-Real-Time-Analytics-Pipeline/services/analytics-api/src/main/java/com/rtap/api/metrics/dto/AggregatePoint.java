package com.rtap.api.metrics.dto;

import java.time.Instant;

/** One windowed aggregate point in a time series — mirrors MetricAggregate (flink-common). */
public record AggregatePoint(
        Instant windowStart,
        long count,
        double sum,
        double min,
        double max,
        double avg
) {
}
