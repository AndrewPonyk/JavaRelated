package com.example.pipeline.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A single immutable sensor reading — the unit of work flowing through the pipeline.
 *
 * <p><strong>Thread safety:</strong> deeply immutable (all components are either
 * primitives, {@code String}, an enum or {@code Instant}), so an instance can be
 * handed between stage threads through a {@code BlockingQueue} with no
 * synchronisation and no defensive copying.
 *
 * <p>Invariants are enforced in the canonical constructor: an invalid event can
 * never exist, so no downstream stage needs a null or NaN check.
 *
 * @param sequence  monotonically increasing id assigned by the producer, {@code >= 0}
 * @param sensorId  logical device identifier, non-blank
 * @param type      what is being measured
 * @param value     the reading, finite (never NaN or infinite)
 * @param timestamp when the reading was taken
 */
public record SensorEvent(long sequence, String sensorId, SensorType type, double value, Instant timestamp) {

    /** Validates the invariants described in the class documentation. */
    public SensorEvent {
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be >= 0 but was " + sequence);
        }
        Objects.requireNonNull(sensorId, "sensorId");
        if (sensorId.isBlank()) {
            throw new IllegalArgumentException("sensorId must not be blank");
        }
        Objects.requireNonNull(type, "type");
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("value must be finite but was " + value);
        }
        Objects.requireNonNull(timestamp, "timestamp");
    }

    /** Unit of {@link #value}, delegated to the sensor type. */
    public String unit() {
        return type.unit();
    }

    /**
     * Threshold test used by the filter stage.
     *
     * <p>Strictly greater-than, so a reading exactly equal to the threshold is
     * rejected. That choice is arbitrary but it must be made in exactly one
     * place, and this is it.
     */
    public boolean exceeds(double threshold) {
        return value > threshold;
    }

    /** Compact, low-cardinality rendering suitable for {@code FINE} level logging. */
    public String toLogLine() {
        return "seq=" + sequence + " sensor=" + sensorId + " type=" + type
                + " value=" + value + unit() + " at=" + timestamp;
    }
}
