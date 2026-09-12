package com.rtap.streaming.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Anomaly-model parameters published by the offline trainer to the compacted
 * {@code ml.model-updates.v1} topic (latest-wins per metricKey) and broadcast into
 * the anomaly job. Contract: ml/README.md.
 *
 * <p>{@code seasonalMeans/seasonalStds} hold one bucket per hour of week
 * (168 entries, Monday 00:00 UTC first). The detector subtracts the seasonal mean
 * before scoring so daily/weekly cycles don't page anyone.
 */
public class ModelParams implements Serializable {

    private static final long serialVersionUID = 1L;
    public static final int HOURS_PER_WEEK = 168;

    private String metricKey;
    private String modelVersion;
    private double zThreshold;
    private double[] seasonalMeans;
    private double[] seasonalStds;

    public ModelParams() {
    }

    /** A model is usable when it has a key and either no seasonality or full 168 buckets. */
    public boolean isValid() {
        if (metricKey == null || metricKey.isBlank()) {
            return false;
        }
        if (seasonalMeans == null) {
            return true;
        }
        return seasonalMeans.length == HOURS_PER_WEEK
                && (seasonalStds == null || seasonalStds.length == HOURS_PER_WEEK);
    }

    /** Seasonal expectation at the given instant; 0 when the model has no seasonality. */
    public double expectedAt(long epochMillis) {
        if (seasonalMeans == null || seasonalMeans.length != HOURS_PER_WEEK) {
            return 0.0;
        }
        return seasonalMeans[hourOfWeek(epochMillis)];
    }

    /** Monday 00:00 UTC = bucket 0 … Sunday 23:00 UTC = bucket 167. */
    public static int hourOfWeek(long epochMillis) {
        ZonedDateTime utc = Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC);
        return (utc.getDayOfWeek().getValue() - 1) * 24 + utc.getHour();
    }

    public String getMetricKey() { return metricKey; }
    public void setMetricKey(String metricKey) { this.metricKey = metricKey; }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }

    // Explicit name: Jackson's bean naming would mangle getZThreshold → "zthreshold"
    // and silently drop the trainer's "zThreshold" field.
    @JsonProperty("zThreshold")
    public double getZThreshold() { return zThreshold; }

    @JsonProperty("zThreshold")
    public void setZThreshold(double zThreshold) { this.zThreshold = zThreshold; }

    public double[] getSeasonalMeans() { return seasonalMeans; }
    public void setSeasonalMeans(double[] seasonalMeans) { this.seasonalMeans = seasonalMeans; }

    public double[] getSeasonalStds() { return seasonalStds; }
    public void setSeasonalStds(double[] seasonalStds) { this.seasonalStds = seasonalStds; }

    @Override
    public String toString() {
        return "ModelParams{%s v=%s z=%.1f seasonal=%s}"
                .formatted(metricKey, modelVersion, zThreshold, seasonalMeans != null);
    }
}
