package com.rtap.api.metrics.dto;

import java.time.Instant;

/** A registered business metric — mirrors the metric_definitions table (V001). */
public record MetricDefinition(
        String metricKey,
        String displayName,
        String unit,
        String description,
        Instant createdAt
) {
}
