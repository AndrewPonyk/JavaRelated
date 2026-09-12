package com.rtap.streaming.common.model;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * One windowed aggregate for one metric key — the pipeline's central product.
 * Written to {@code metrics.aggregates.v1} (transactional), Elasticsearch, and PostgreSQL.
 *
 * <p><b>Idempotence contract:</b> {@link #documentId()} is deterministic over
 * {@code (metricKey, windowSize, windowStart, dimensions)}. It is used as the
 * Elasticsearch {@code _id} and mirrors the PostgreSQL primary key, so replays
 * after a failure <em>overwrite</em> instead of duplicating — that is what turns
 * at-least-once sinks into effectively-once delivery (ARCHITECTURE.md §2.3).
 */
public class MetricAggregate implements Serializable {

    private static final long serialVersionUID = 1L;

    private String metricKey;      // e.g. "orders.completed"
    private String windowSize;     // "1s" | "1m"
    private long windowStart;      // epoch millis UTC, inclusive
    private long windowEnd;        // epoch millis UTC, exclusive
    private long count;
    private double sum;
    private double min;
    private double max;
    private Map<String, String> dimensions = new HashMap<>();

    public MetricAggregate() {
    }

    public double getAvg() {
        return count == 0 ? 0.0 : sum / count;
    }

    /** Deterministic upsert key: metricKey|windowSize|windowStart|dimHash. */
    public String documentId() {
        return metricKey + '|' + windowSize + '|' + windowStart + '|' + dimensionsHash();
    }

    /** Stable short hash of the (sorted) dimension map; "0" when empty. */
    public String dimensionsHash() {
        return hashDimensions(dimensions);
    }

    /** Static variant used by key selectors (window keying by metric series). */
    public static String hashDimensions(Map<String, String> dims) {
        if (dims == null || dims.isEmpty()) {
            return "0";
        }
        StringBuilder sb = new StringBuilder();
        new TreeMap<>(dims).forEach((k, v) -> sb.append(k).append('=').append(v).append(';'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8; i++) { // 8 bytes = 16 hex chars is plenty for tag maps
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e); // cannot happen on a JVM
        }
    }

    public String getMetricKey() { return metricKey; }
    public void setMetricKey(String metricKey) { this.metricKey = metricKey; }

    public String getWindowSize() { return windowSize; }
    public void setWindowSize(String windowSize) { this.windowSize = windowSize; }

    public long getWindowStart() { return windowStart; }
    public void setWindowStart(long windowStart) { this.windowStart = windowStart; }

    public long getWindowEnd() { return windowEnd; }
    public void setWindowEnd(long windowEnd) { this.windowEnd = windowEnd; }

    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }

    public double getSum() { return sum; }
    public void setSum(double sum) { this.sum = sum; }

    public double getMin() { return min; }
    public void setMin(double min) { this.min = min; }

    public double getMax() { return max; }
    public void setMax(double max) { this.max = max; }

    public Map<String, String> getDimensions() { return dimensions; }
    /** Defensive copy: Flink's serializers require mutable map instances. */
    public void setDimensions(Map<String, String> dimensions) {
        this.dimensions = dimensions == null ? new HashMap<>() : new HashMap<>(dimensions);
    }

    @Override
    public String toString() {
        return "MetricAggregate{%s %s @%d count=%d sum=%s}"
                .formatted(metricKey, windowSize, windowStart, count, sum);
    }
}
