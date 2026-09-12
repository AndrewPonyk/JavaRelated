package com.example.pipeline.application.filter;

import com.example.pipeline.application.port.EventPredicate;
import com.example.pipeline.domain.SensorEvent;
import com.example.pipeline.domain.SensorType;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Passes events whose value is strictly greater than a threshold.
 *
 * <p>A global threshold applies unless a per-{@link SensorType} override is
 * configured — comparing a humidity percentage against the same number as a
 * pressure reading in kPa is rarely what anyone wants.
 *
 * <p><strong>Thread safety:</strong> the global threshold is {@code volatile} and
 * may be changed at runtime (the optional HTTP control plane does exactly that).
 * A concurrent change is safely published to every consumer thread; events already
 * being evaluated may use either the old or the new value, which is the correct
 * semantics for a live tuning knob. The override map is immutable and fixed at
 * construction.
 */
public final class ThresholdPredicate implements EventPredicate {

    private final Map<SensorType, Double> overrides;
    private volatile double threshold;

    /** Predicate with a single global threshold. */
    public ThresholdPredicate(double threshold) {
        this(threshold, Map.of());
    }

    /**
     * @param threshold global threshold applied when no override matches
     * @param overrides per-sensor-type thresholds; copied defensively
     */
    public ThresholdPredicate(double threshold, Map<SensorType, Double> overrides) {
        requireFinite(threshold);
        Objects.requireNonNull(overrides, "overrides");
        overrides.forEach((type, value) -> {
            Objects.requireNonNull(type, "override sensor type");
            requireFinite(Objects.requireNonNull(value, "override threshold"));
        });
        this.threshold = threshold;
        this.overrides = overrides.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(overrides));
    }

    @Override
    public boolean test(SensorEvent event) {
        Objects.requireNonNull(event, "event");
        Double override = overrides.get(event.type());
        double effective = override != null ? override : threshold;
        return event.exceeds(effective);
    }

    @Override
    public String description() {
        StringBuilder text = new StringBuilder(String.format(Locale.ROOT, "value > %.3f", threshold));
        if (!overrides.isEmpty()) {
            text.append(" (overrides: ").append(overrides).append(')');
        }
        return text.toString();
    }

    /** Current global threshold. */
    public double threshold() {
        return threshold;
    }

    /**
     * Updates the global threshold while the pipeline runs.
     *
     * @throws IllegalArgumentException if not finite — validating here rather than
     *         only in the HTTP handler means every future caller is covered too
     */
    public void setThreshold(double newThreshold) {
        requireFinite(newThreshold);
        this.threshold = newThreshold;
    }

    private static void requireFinite(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("threshold must be a finite number but was " + value);
        }
    }
}
