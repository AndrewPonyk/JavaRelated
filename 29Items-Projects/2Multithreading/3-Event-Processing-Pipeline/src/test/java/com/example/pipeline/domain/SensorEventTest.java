package com.example.pipeline.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.pipeline.TestEvents;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link SensorEvent}.
 *
 * <p>Most of the value here is in the rejection cases. A {@code SensorEvent} carrying
 * {@code NaN} does not fail where it was created — it fails much later, in
 * {@code AggregateResult}, where {@code Math.min(NaN, x)} is {@code NaN} and poisons a
 * sensor's aggregate for the rest of the run with no exception anywhere. The compact
 * constructor is the only place that can stop it, so it is the place worth testing.
 */
class SensorEventTest {

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("rejects a negative sequence")
        void rejectsNegativeSequence() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> new SensorEvent(-1L, "sensor-00", SensorType.TEMPERATURE, 1.0, TestEvents.FIXED_TIME));
            assertTrue(thrown.getMessage().contains("sequence"), thrown.getMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "\t", "\n"})
        @DisplayName("rejects a blank sensor id")
        void rejectsBlankSensorId(String sensorId) {
            assertThrows(IllegalArgumentException.class,
                    () -> new SensorEvent(0L, sensorId, SensorType.TEMPERATURE, 1.0, TestEvents.FIXED_TIME));
        }

        @Test
        @DisplayName("rejects a null sensor id, type and timestamp")
        void rejectsNulls() {
            assertThrows(NullPointerException.class,
                    () -> new SensorEvent(0L, null, SensorType.TEMPERATURE, 1.0, TestEvents.FIXED_TIME));
            assertThrows(NullPointerException.class,
                    () -> new SensorEvent(0L, "sensor-00", null, 1.0, TestEvents.FIXED_TIME));
            assertThrows(NullPointerException.class,
                    () -> new SensorEvent(0L, "sensor-00", SensorType.TEMPERATURE, 1.0, null));
        }

        /**
         * The important one. These three values propagate silently through every
         * downstream comparison instead of failing where they were introduced.
         */
        @ParameterizedTest
        @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
        @DisplayName("rejects a non-finite value")
        void rejectsNonFiniteValue(double value) {
            assertThrows(IllegalArgumentException.class,
                    () -> new SensorEvent(0L, "sensor-00", SensorType.TEMPERATURE, value, TestEvents.FIXED_TIME));
        }

        @Test
        @DisplayName("accepts zero and a negative reading")
        void acceptsZeroAndNegative() {
            // -40 degC is inside TEMPERATURE's range; the record must not confuse
            // "outside the sensor's plausible range" with "invalid".
            assertEquals(-40.0, TestEvents.event("sensor-00", -40.0).value());
            assertEquals(0.0, TestEvents.event("sensor-00", 0.0).value());
        }
    }

    @Nested
    @DisplayName("exceeds")
    class Exceeds {

        /**
         * Strictly greater than, and this test exists to pin that down. Whether a
         * reading exactly equal to the threshold passes is arbitrary; that the answer
         * is the same everywhere is not, and {@code exceeds} is the single place the
         * decision is made.
         */
        @Test
        @DisplayName("is strictly greater than, so equality does not pass")
        void equalityDoesNotPass() {
            SensorEvent event = TestEvents.event("sensor-00", 50.0);
            assertFalse(event.exceeds(50.0), "a reading equal to the threshold must not pass");
            assertTrue(event.exceeds(49.999));
            assertFalse(event.exceeds(50.001));
        }

        @Test
        @DisplayName("works across zero")
        void worksAcrossZero() {
            assertTrue(TestEvents.event("sensor-00", -1.0).exceeds(-2.0));
            assertFalse(TestEvents.event("sensor-00", -2.0).exceeds(-1.0));
        }
    }

    @Nested
    @DisplayName("derived accessors")
    class Derived {

        @ParameterizedTest
        @EnumSource(SensorType.class)
        @DisplayName("unit is delegated to the type, for every type")
        void unitIsDelegated(SensorType type) {
            // EnumSource rather than four hand-written cases: a fifth SensorType added
            // later is covered automatically instead of being silently untested.
            assertEquals(type.unit(), TestEvents.event("sensor-00", type, 1.0).unit());
        }

        @Test
        @DisplayName("toLogLine carries the fields a reader needs to correlate")
        void logLineCarriesTheFields() {
            SensorEvent event = new SensorEvent(42L, "sensor-07", SensorType.PRESSURE,
                    101.5, Instant.parse("2026-01-01T00:00:00Z"));
            String line = event.toLogLine();
            // Asserting on the tokens, not the exact string: the format is a
            // convenience and may be reflowed, but losing the sequence number would
            // make a log line impossible to line up with anything.
            assertTrue(line.contains("seq=42"), line);
            assertTrue(line.contains("sensor=sensor-07"), line);
            assertTrue(line.contains("PRESSURE"), line);
            assertTrue(line.contains("kPa"), line);
        }
    }

    @Test
    @DisplayName("records compare by value, which is what lets tests assert on them")
    void recordsCompareByValue() {
        SensorEvent left = new SensorEvent(1L, "sensor-00", SensorType.HUMIDITY, 12.5, TestEvents.FIXED_TIME);
        SensorEvent right = new SensorEvent(1L, "sensor-00", SensorType.HUMIDITY, 12.5, TestEvents.FIXED_TIME);
        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
    }
}
