package com.example.pipeline;

import com.example.pipeline.domain.EventBatch;
import com.example.pipeline.domain.SensorEvent;
import com.example.pipeline.domain.SensorType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Event builders shared by the test suite.
 *
 * <p>Exists so that a test which cares about <em>one</em> field — a value, a sensor id —
 * does not have to restate the other four. A test whose setup is five lines of
 * irrelevant arguments hides its own point.
 *
 * <p>Every factory here is deterministic. No {@code Instant.now()}, no random values:
 * a concurrency test that fails is hard enough to diagnose without the input having
 * been different on the run that passed.
 */
public final class TestEvents {

    /** Fixed timestamp for every generated event. The value is arbitrary; the fixity is not. */
    public static final Instant FIXED_TIME = Instant.parse("2026-01-01T00:00:00Z");

    private TestEvents() {
        throw new AssertionError("utility class");
    }

    /** One event with an explicit sensor id and value; sequence and type are conventional. */
    public static SensorEvent event(String sensorId, double value) {
        return new SensorEvent(0L, sensorId, SensorType.TEMPERATURE, value, FIXED_TIME);
    }

    /** One event with an explicit sequence, sensor id and value. */
    public static SensorEvent event(long sequence, String sensorId, double value) {
        return new SensorEvent(sequence, sensorId, SensorType.TEMPERATURE, value, FIXED_TIME);
    }

    /** One event of a specific type — for the per-type override tests. */
    public static SensorEvent event(String sensorId, SensorType type, double value) {
        return new SensorEvent(0L, sensorId, type, value, FIXED_TIME);
    }

    /**
     * {@code count} events spread over {@code sensorCount} sensors, values {@code 0..count-1}.
     *
     * <p>Ascending values are deliberate: a threshold of {@code k} then passes exactly
     * {@code count - k - 1} events, so the expected count is arithmetic rather than
     * something the test has to count by hand.
     */
    public static List<SensorEvent> events(int count, int sensorCount) {
        List<SensorEvent> events = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            events.add(event(i, "sensor-" + (i % sensorCount), i));
        }
        return List.copyOf(events);
    }

    /** A batch of {@code count} events over {@code sensorCount} sensors. */
    public static EventBatch batch(long batchId, int count, int sensorCount) {
        return new EventBatch(batchId, events(count, sensorCount));
    }
}
