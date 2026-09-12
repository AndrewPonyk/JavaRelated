package com.example.pipeline.domain;

/**
 * Kind of sensor a {@link SensorEvent} originates from.
 *
 * <p>Each constant carries the plausible value range used by the synthetic
 * generator plus the display unit. Keeping the range here (rather than in the
 * generator) means the domain owns what a "valid reading" looks like, and the
 * generator stays a dumb adapter.
 *
 * <p>Enums are immutable and therefore safe to share across all pipeline threads.
 */
public enum SensorType {

    /** Ambient temperature in degrees Celsius. */
    TEMPERATURE("degC", -40.0, 125.0),

    /** Absolute pressure in kilopascal. */
    PRESSURE("kPa", 0.0, 1000.0),

    /** Relative humidity, percent. */
    HUMIDITY("pct", 0.0, 100.0),

    /** Vibration velocity in millimetres per second. */
    VIBRATION("mm/s", 0.0, 50.0);

    private final String unit;
    private final double minValue;
    private final double maxValue;

    SensorType(String unit, double minValue, double maxValue) {
        this.unit = unit;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    /** Display unit, e.g. {@code degC}. */
    public String unit() {
        return unit;
    }

    /** Lowest physically plausible reading for this sensor type. */
    public double minValue() {
        return minValue;
    }

    /** Highest physically plausible reading for this sensor type. */
    public double maxValue() {
        return maxValue;
    }

    /** Width of the plausible range; used to map a uniform [0,1) sample onto a reading. */
    public double span() {
        return maxValue - minValue;
    }

    /** {@code true} when {@code value} falls inside this sensor's plausible range. */
    public boolean isInRange(double value) {
        return Double.isFinite(value) && value >= minValue && value <= maxValue;
    }
}
