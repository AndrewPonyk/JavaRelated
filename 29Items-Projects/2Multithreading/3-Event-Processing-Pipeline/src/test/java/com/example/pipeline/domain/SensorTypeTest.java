package com.example.pipeline.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The physical ranges the domain owns, and the two ways they get used.
 *
 * <p>{@link #spanIsPositiveForEveryType()} and {@link #rangesAreWellFormed()} are the tests
 * that matter, because the generator maps a uniform {@code [0,1)} sample onto
 * {@code min + sample * span()}. A type declared with its bounds swapped would give a
 * negative span and generate readings <em>below</em> its own minimum — values that then fail
 * {@link SensorType#isInRange(double)} for data the pipeline itself produced.
 *
 * <p>{@link #nonFiniteValuesAreNeverInRange()} covers the case the plain comparison misses:
 * {@code NaN >= min} is false, so NaN happens to fall out, but {@code Infinity} does not, and
 * an infinite reading reaching the aggregator poisons its mean for the whole run.
 */
@Timeout(10)
@DisplayName("SensorType")
class SensorTypeTest {

    @Test
    @DisplayName("the four sensor kinds are the ones the generator and the report agree on")
    void theEnumIsStable() {
        // The CSV report and the console table are keyed by these names, so adding or
        // renaming a constant is a change to the output format, not an internal detail.
        assertEquals(4, SensorType.values().length);
        assertEquals("TEMPERATURE", SensorType.TEMPERATURE.name());
        assertEquals("PRESSURE", SensorType.PRESSURE.name());
        assertEquals("HUMIDITY", SensorType.HUMIDITY.name());
        assertEquals("VIBRATION", SensorType.VIBRATION.name());
    }

    @ParameterizedTest
    @DisplayName("each type carries the unit its readings are displayed in")
    @CsvSource({"TEMPERATURE, degC", "PRESSURE, kPa", "HUMIDITY, pct", "VIBRATION, mm/s"})
    void unitsAreDeclared(SensorType type, String unit) {
        assertEquals(unit, type.unit());
    }

    @ParameterizedTest
    @DisplayName("every unit is short, non-blank and ASCII, so a console table stays aligned")
    @EnumSource(SensorType.class)
    void unitsArePrintable(SensorType type) {
        // A degree sign or a mu would be mojibake on a cp1252 console -- "degC" and "mm/s"
        // are deliberately spelled out for that reason.
        assertFalse(type.unit().isBlank(), type.name());
        assertTrue(type.unit().length() <= 4, type.unit());
        assertTrue(type.unit().chars().allMatch(c -> c < 0x80), type.unit());
    }

    @ParameterizedTest
    @DisplayName("every range is min < max with finite bounds")
    @EnumSource(SensorType.class)
    void rangesAreWellFormed(SensorType type) {
        assertTrue(Double.isFinite(type.minValue()), type.name());
        assertTrue(Double.isFinite(type.maxValue()), type.name());
        assertTrue(type.minValue() < type.maxValue(),
                type.name() + " has its bounds the wrong way round");
    }

    /**
     * The generator computes {@code min + sample * span()}. A non-positive span would either
     * pin every reading of that type to one value or generate readings below the declared
     * minimum, and the pipeline would then reject its own synthetic data.
     */
    @ParameterizedTest
    @DisplayName("span is positive, so a uniform sample maps onto a real interval")
    @EnumSource(SensorType.class)
    void spanIsPositiveForEveryType(SensorType type) {
        assertTrue(type.span() > 0.0, type.name() + " span=" + type.span());
        assertEquals(type.maxValue() - type.minValue(), type.span(), 0.0);
    }

    @ParameterizedTest
    @DisplayName("both bounds are inside the range - the interval is closed")
    @EnumSource(SensorType.class)
    void boundsAreInclusive(SensorType type) {
        // Half-open would make the generator's maximum reading invalid on the last sample.
        assertTrue(type.isInRange(type.minValue()), type.name());
        assertTrue(type.isInRange(type.maxValue()), type.name());
    }

    @ParameterizedTest
    @DisplayName("a value just outside either bound is out of range")
    @EnumSource(SensorType.class)
    void valuesOutsideTheBoundsAreRejected(SensorType type) {
        assertFalse(type.isInRange(Math.nextDown(type.minValue())), type.name() + " below min");
        assertFalse(type.isInRange(Math.nextUp(type.maxValue())), type.name() + " above max");
    }

    @ParameterizedTest
    @DisplayName("the midpoint of every range is in range")
    @EnumSource(SensorType.class)
    void midpointIsInRange(SensorType type) {
        assertTrue(type.isInRange(type.minValue() + type.span() / 2.0), type.name());
    }

    /**
     * NaN falls out of the comparison by accident; the infinities do not. An infinite reading
     * that reached the aggregator would make that sensor's mean infinite for the whole run,
     * and the report would show it for every batch afterwards.
     */
    @ParameterizedTest
    @DisplayName("NaN and the infinities are never in range")
    @EnumSource(SensorType.class)
    void nonFiniteValuesAreNeverInRange(SensorType type) {
        assertFalse(type.isInRange(Double.NaN), type.name() + " accepted NaN");
        assertFalse(type.isInRange(Double.POSITIVE_INFINITY), type.name() + " accepted +Inf");
        assertFalse(type.isInRange(Double.NEGATIVE_INFINITY), type.name() + " accepted -Inf");
    }

    @Test
    @DisplayName("humidity is a percentage, so its range is exactly 0..100")
    void humidityRangeIsThePhysicalOne() {
        // Spot-check one concrete range: the values are physical facts, not tuning knobs,
        // and a change to them is a change to what the generator claims to simulate.
        assertEquals(0.0, SensorType.HUMIDITY.minValue(), 0.0);
        assertEquals(100.0, SensorType.HUMIDITY.maxValue(), 0.0);
        assertFalse(SensorType.HUMIDITY.isInRange(101.0));
    }

    @Test
    @DisplayName("temperature is the one type whose minimum is below zero")
    void temperatureAllowsNegativeReadings() {
        assertTrue(SensorType.TEMPERATURE.minValue() < 0.0);
        assertTrue(SensorType.TEMPERATURE.isInRange(-40.0));
        assertFalse(SensorType.TEMPERATURE.isInRange(-40.5));
        // The filter threshold defaults to 50.0, which only makes sense against ranges that
        // straddle it -- a range entirely below the threshold would pass nothing.
        assertTrue(SensorType.TEMPERATURE.maxValue() > 50.0);
    }

    @ParameterizedTest
    @DisplayName("valueOf round-trips every constant, so a persisted name still resolves")
    @EnumSource(SensorType.class)
    void valueOfRoundTrips(SensorType type) {
        assertEquals(type, SensorType.valueOf(type.name()));
    }
}
