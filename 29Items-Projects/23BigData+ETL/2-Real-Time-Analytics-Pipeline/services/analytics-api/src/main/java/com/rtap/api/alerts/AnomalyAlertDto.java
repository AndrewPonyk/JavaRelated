package com.rtap.api.alerts;

import java.time.Instant;

/** Anomaly alert as served to the UI — mirrors the anomaly_alerts table (V001). */
public record AnomalyAlertDto(
        String alertId,
        String metricKey,
        String severity,   // warning | serious | critical (status palette in the UI)
        double score,      // signed z-score: + spike, − drop
        double observed,
        double expected,
        Instant windowStart,
        Instant detectedAt,
        String status,     // open | acknowledged | resolved
        String ackedBy
) {
}
