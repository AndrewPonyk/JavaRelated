package com.rtap.streaming.common.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * An anomaly detected on the aggregate stream; produced to {@code alerts.anomalies.v1},
 * persisted by the API into PostgreSQL (alert workflow) and pushed to the UI via SSE.
 */
public class AnomalyAlert implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Severity ladder — mirrored by the UI status palette and Grafana thresholds. */
    public static final String SEVERITY_WARNING = "warning";
    public static final String SEVERITY_SERIOUS = "serious";
    public static final String SEVERITY_CRITICAL = "critical";

    private String alertId;
    private String metricKey;
    private String detector;       // "ewma-zscore" | "seasonal-baseline" (model-driven)
    private String modelVersion;   // version of broadcast model params, "-" for online-only
    private double score;          // z-score (signed: + spike, − drop)
    private double threshold;
    private double observed;       // the aggregate value that triggered
    private double expected;       // detector's expectation (EWMA mean / seasonal baseline)
    private String severity;
    private long windowStart;
    private long windowEnd;
    private long detectedAt;       // processing time, epoch millis
    private Map<String, String> dimensions = new HashMap<>();

    public AnomalyAlert() {
    }

    public static AnomalyAlert of(MetricAggregate agg, String detector, double score,
                                  double threshold, double observed, double expected) {
        AnomalyAlert a = new AnomalyAlert();
        a.alertId = UUID.randomUUID().toString();
        a.metricKey = agg.getMetricKey();
        a.detector = detector;
        a.modelVersion = "-";
        a.score = score;
        a.threshold = threshold;
        a.observed = observed;
        a.expected = expected;
        a.severity = severityFor(score, threshold);
        a.windowStart = agg.getWindowStart();
        a.windowEnd = agg.getWindowEnd();
        a.detectedAt = System.currentTimeMillis();
        a.dimensions = agg.getDimensions();
        return a;
    }

    /** |z| ≥ 2×threshold → critical, ≥ 1.5× → serious, else warning. */
    static String severityFor(double score, double threshold) {
        double magnitude = Math.abs(score);
        if (magnitude >= 2.0 * threshold) return SEVERITY_CRITICAL;
        if (magnitude >= 1.5 * threshold) return SEVERITY_SERIOUS;
        return SEVERITY_WARNING;
    }

    public String getAlertId() { return alertId; }
    public void setAlertId(String alertId) { this.alertId = alertId; }

    public String getMetricKey() { return metricKey; }
    public void setMetricKey(String metricKey) { this.metricKey = metricKey; }

    public String getDetector() { return detector; }
    public void setDetector(String detector) { this.detector = detector; }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }

    public double getObserved() { return observed; }
    public void setObserved(double observed) { this.observed = observed; }

    public double getExpected() { return expected; }
    public void setExpected(double expected) { this.expected = expected; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public long getWindowStart() { return windowStart; }
    public void setWindowStart(long windowStart) { this.windowStart = windowStart; }

    public long getWindowEnd() { return windowEnd; }
    public void setWindowEnd(long windowEnd) { this.windowEnd = windowEnd; }

    public long getDetectedAt() { return detectedAt; }
    public void setDetectedAt(long detectedAt) { this.detectedAt = detectedAt; }

    public Map<String, String> getDimensions() { return dimensions; }
    /** Defensive copy: Flink's serializers require mutable map instances. */
    public void setDimensions(Map<String, String> dimensions) {
        this.dimensions = dimensions == null ? new HashMap<>() : new HashMap<>(dimensions);
    }

    @Override
    public String toString() {
        return "AnomalyAlert{%s %s z=%.2f observed=%.2f expected=%.2f}"
                .formatted(metricKey, severity, score, observed, expected);
    }
}
