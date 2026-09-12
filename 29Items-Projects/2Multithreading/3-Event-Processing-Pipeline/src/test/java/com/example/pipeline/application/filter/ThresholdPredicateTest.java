package com.example.pipeline.application.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.pipeline.TestEvents;
import com.example.pipeline.domain.SensorType;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link ThresholdPredicate}.
 *
 * <p>Two things here are worth more than the obvious pass/reject cases. The first is
 * the per-type override: without it a single number is compared against a humidity
 * percentage and a pressure in kPa alike, so every pressure reading passes and the
 * filter looks like it is working. The second is {@code setThreshold} — it is called
 * from an HTTP request thread while N consumer threads are inside {@code test}, so the
 * validation has to live in the setter rather than in the handler, and the field has to
 * be {@code volatile} or a consumer may never observe the change at all.
 */
class ThresholdPredicateTest {

    @Nested
    @DisplayName("global threshold")
    class Global {

        @Test
        @DisplayName("passes only readings strictly above the threshold")
        void passesStrictlyAbove() {
            ThresholdPredicate predicate = new ThresholdPredicate(50.0);

            assertTrue(predicate.test(TestEvents.event("sensor-00", 50.001)));
            assertFalse(predicate.test(TestEvents.event("sensor-00", 50.0)), "equal must not pass");
            assertFalse(predicate.test(TestEvents.event("sensor-00", 49.999)));
        }

        @Test
        @DisplayName("a negative threshold works, so the filter can be effectively open")
        void negativeThresholdIsUsable() {
            // The generator's TEMPERATURE range starts below zero; a threshold that
            // cannot go negative would make "pass everything" impossible to express.
            ThresholdPredicate predicate = new ThresholdPredicate(-100.0);
            assertTrue(predicate.test(TestEvents.event("sensor-00", -40.0)));
        }
    }

    @Nested
    @DisplayName("per-type overrides")
    class Overrides {

        @Test
        @DisplayName("an override replaces the global threshold for that type only")
        void overrideAppliesToItsTypeOnly() {
            ThresholdPredicate predicate = new ThresholdPredicate(
                    0.0, Map.of(SensorType.HUMIDITY, 90.0));

            // HUMIDITY is judged against 90, not against the global 0.
            assertFalse(predicate.test(TestEvents.event("sensor-00", SensorType.HUMIDITY, 55.0)));
            assertTrue(predicate.test(TestEvents.event("sensor-00", SensorType.HUMIDITY, 95.0)));
            // Every other type still uses the global threshold.
            assertTrue(predicate.test(TestEvents.event("sensor-00", SensorType.TEMPERATURE, 55.0)));
        }

        /**
         * The map is copied at construction. A caller that keeps a reference to the map
         * it passed in must not be able to retune the filter by mutating it — that is a
         * data race on a field the predicate documents as fixed.
         */
        @Test
        @DisplayName("mutating the caller's map afterwards has no effect")
        void overridesAreCopiedDefensively() {
            Map<SensorType, Double> mutable = new HashMap<>();
            mutable.put(SensorType.HUMIDITY, 90.0);
            ThresholdPredicate predicate = new ThresholdPredicate(0.0, mutable);

            mutable.put(SensorType.HUMIDITY, -1000.0);
            mutable.put(SensorType.PRESSURE, -1000.0);

            assertFalse(predicate.test(TestEvents.event("sensor-00", SensorType.HUMIDITY, 55.0)),
                    "the override in force must still be the one captured at construction");
        }

        @Test
        @DisplayName("rejects a non-finite override at construction")
        void rejectsNonFiniteOverride() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ThresholdPredicate(0.0, Map.of(SensorType.HUMIDITY, Double.NaN)));
        }
    }

    @Nested
    @DisplayName("setThreshold")
    class Retuning {

        @Test
        @DisplayName("a new threshold takes effect on the next event")
        void takesEffectImmediately() {
            ThresholdPredicate predicate = new ThresholdPredicate(0.0);
            assertTrue(predicate.test(TestEvents.event("sensor-00", 10.0)));

            predicate.setThreshold(100.0);

            assertEquals(100.0, predicate.threshold());
            assertFalse(predicate.test(TestEvents.event("sensor-00", 10.0)));
        }

        /**
         * The one that matters. {@code NaN} here would not throw and would not log:
         * {@code value > NaN} is {@code false} for every value, so the filter silently
         * rejects everything for the rest of the run and the report is all zeroes.
         */
        @ParameterizedTest
        @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
        @DisplayName("refuses a non-finite threshold and keeps the old one")
        void refusesNonFinite(double bad) {
            ThresholdPredicate predicate = new ThresholdPredicate(42.0);

            assertThrows(IllegalArgumentException.class, () -> predicate.setThreshold(bad));
            assertEquals(42.0, predicate.threshold(), "a rejected update must not have been applied");
        }
    }

    @Nested
    @DisplayName("description")
    class Description {

        /**
         * {@code description()} goes into the report and the {@code /metrics} response,
         * where it is the only record of what the filter actually did. A description
         * that omitted the overrides would make two very different runs look identical.
         */
        @Test
        @DisplayName("states the threshold, and the overrides when there are any")
        void statesTheEffectiveConfiguration() {
            assertEquals("value > 50.000", new ThresholdPredicate(50.0).description());

            String withOverrides = new ThresholdPredicate(50.0, Map.of(SensorType.HUMIDITY, 90.0)).description();
            assertTrue(withOverrides.startsWith("value > 50.000"), withOverrides);
            assertTrue(withOverrides.contains("HUMIDITY"), withOverrides);
        }
    }
}
