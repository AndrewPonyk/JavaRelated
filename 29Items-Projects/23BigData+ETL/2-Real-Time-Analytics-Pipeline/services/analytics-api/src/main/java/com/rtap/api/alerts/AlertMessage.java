package com.rtap.api.alerts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Wire format of {@code alerts.anomalies.v1} — mirrors AnomalyAlert (flink-common).
 * Timestamps are epoch millis on the wire; the repository converts to TIMESTAMPTZ.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlertMessage(
        String alertId,
        String metricKey,
        String detector,
        String modelVersion,
        double score,
        double threshold,
        double observed,
        double expected,
        String severity,
        long windowStart,
        long windowEnd,
        long detectedAt,
        Map<String, String> dimensions
) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String dimensionsJson() {
        try {
            return MAPPER.writeValueAsString(dimensions == null ? Map.of() : dimensions);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unserializable dimensions map", e);
        }
    }
}
