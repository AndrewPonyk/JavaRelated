package com.example.pipeline.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * Per-sensor aggregate: count, min, max and sum of the readings that passed the filter.
 *
 * <p><strong>Why this type is the heart of the design:</strong> {@link #merge} is
 * associative, commutative and has an identity ({@link #identity(String)}), i.e.
 * aggregates form a commutative monoid. That is precisely the property that makes
 * fork/join decomposition legal — partial results can be combined in any order and
 * any grouping and still yield the same answer. Any future sharded or distributed
 * aggregation is legal for the same reason.
 *
 * <p>{@code sum} is kept instead of a running mean so that merging stays exact and
 * order-independent; {@link #average()} is derived on read.
 *
 * <p><strong>Thread safety:</strong> immutable. Merging produces a new instance
 * rather than mutating either operand.
 *
 * @param sensorId the sensor these numbers belong to
 * @param count    how many readings were folded in, {@code >= 0}
 * @param min      smallest reading, {@code +Infinity} for the identity
 * @param max      largest reading, {@code -Infinity} for the identity
 * @param sum      sum of all readings
 */
public record AggregateResult(String sensorId, long count, double min, double max, double sum) {

    /** Rejects negative counts and non-finite values for non-empty aggregates. */
    public AggregateResult {
        Objects.requireNonNull(sensorId, "sensorId");
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0 but was " + count);
        }
        if (count > 0) {
            if (!Double.isFinite(min) || !Double.isFinite(max) || !Double.isFinite(sum)) {
                throw new IllegalArgumentException(
                        "non-empty aggregate for " + sensorId + " must have finite min/max/sum");
            }
            if (min > max) {
                throw new IllegalArgumentException("min " + min + " must not exceed max " + max);
            }
        }
    }

    /**
     * The neutral element of {@link #merge}: merging it with any aggregate for the
     * same sensor returns that aggregate unchanged.
     */
    public static AggregateResult identity(String sensorId) {
        return new AggregateResult(sensorId, 0L, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0);
    }

    /** Single-event aggregate — the leaf value of the fold. */
    public static AggregateResult of(SensorEvent event) {
        Objects.requireNonNull(event, "event");
        return new AggregateResult(event.sensorId(), 1L, event.value(), event.value(), event.value());
    }

    /** Folds one more reading in, returning a new aggregate. */
    public AggregateResult accumulate(SensorEvent event) {
        Objects.requireNonNull(event, "event");
        if (!sensorId.equals(event.sensorId())) {
            throw new IllegalArgumentException(
                    "cannot accumulate event from " + event.sensorId() + " into aggregate for " + sensorId);
        }
        return new AggregateResult(sensorId, count + 1,
                Math.min(min, event.value()), Math.max(max, event.value()), sum + event.value());
    }

    /**
     * Combines two aggregates for the same sensor.
     *
     * @throws IllegalArgumentException if the sensor ids differ — silently merging
     *         unrelated sensors would corrupt the report in a way no test would notice
     */
    public AggregateResult merge(AggregateResult other) {
        Objects.requireNonNull(other, "other");
        if (!sensorId.equals(other.sensorId)) {
            throw new IllegalArgumentException("cannot merge aggregates for " + sensorId + " and " + other.sensorId);
        }
        if (other.count == 0) {
            return this;
        }
        if (count == 0) {
            return other;
        }
        return new AggregateResult(sensorId, count + other.count,
                Math.min(min, other.min), Math.max(max, other.max), sum + other.sum);
    }

    /** Arithmetic mean, or {@code 0.0} for an empty aggregate (never NaN). */
    public double average() {
        return count == 0 ? 0.0 : sum / count;
    }

    /** {@code true} when no readings have been folded in. */
    public boolean isEmpty() {
        return count == 0;
    }

    /** Fixed-width row for the console report. */
    public String toDisplayRow() {
        if (count == 0) {
            return String.format(Locale.ROOT, "%-12s %8d %12s %12s %12s", sensorId, 0L, "-", "-", "-");
        }
        return String.format(Locale.ROOT, "%-12s %8d %12.3f %12.3f %12.3f", sensorId, count, min, max, average());
    }
}
