package com.parallelimage.core.util;

import java.util.Objects;

/**
 * Argument validation helpers.
 *
 * <p>Deliberately throws {@link IllegalArgumentException} / {@link NullPointerException} rather than
 * a domain exception: these signal a <em>programming</em> error at a construction site, not bad user
 * input. User input is validated separately and aggregated into
 * {@link com.parallelimage.core.error.ValidationException}.
 *
 * <p>Thread-safe (stateless).
 */
public final class Preconditions {

    private Preconditions() {
        throw new AssertionError("no instances");
    }

    public static <T> T requireNonNull(T value, String name) {
        return Objects.requireNonNull(value, () -> name + " must not be null");
    }

    public static String requireNonBlank(String value, String name) {
        requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be > 0 but was " + value);
        }
        return value;
    }

    public static long requirePositive(long value, String name) {
        if (value <= 0L) {
            throw new IllegalArgumentException(name + " must be > 0 but was " + value);
        }
        return value;
    }

    public static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0 but was " + value);
        }
        return value;
    }

    public static long requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must be >= 0 but was " + value);
        }
        return value;
    }

    public static double requireInRange(double value, double min, double max, String name) {
        if (Double.isNaN(value) || value < min || value > max) {
            throw new IllegalArgumentException(
                    name + " must be within [" + min + ", " + max + "] but was " + value);
        }
        return value;
    }

    public static void requireState(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
