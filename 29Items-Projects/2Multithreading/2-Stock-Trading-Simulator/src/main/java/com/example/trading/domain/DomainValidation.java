package com.example.trading.domain;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class DomainValidation {
    public static final long MAX_QUANTITY = 1_000_000_000L;

    private static final int MAX_MONEY_PRECISION = 24;
    private static final int MAX_MONEY_SCALE = 4;
    private static final Pattern SYMBOL_PATTERN =
            Pattern.compile("[A-Z][A-Z0-9.-]{0,14}");
    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private DomainValidation() {
    }

    public static String symbol(String value, String field) {
        String normalized = text(value, field, 15).toUpperCase(Locale.ROOT);
        if (!SYMBOL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    field + " must contain 1-15 letters, digits, dots, or hyphens");
        }
        return normalized;
    }

    public static String identifier(String value, String field) {
        String normalized = text(value, field, 64);
        if (!IDENTIFIER_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    field + " must contain 1-64 letters, digits, dots, underscores, or hyphens");
        }
        return normalized;
    }

    public static String text(String value, String field, int maximumLength) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(
                    field + " must not exceed " + maximumLength + " characters");
        }
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " must not contain control characters");
        }
        return normalized;
    }

    public static long quantity(long value, String field) {
        if (value <= 0 || value > MAX_QUANTITY) {
            throw new IllegalArgumentException(
                    field + " must be between 1 and " + MAX_QUANTITY);
        }
        return value;
    }

    public static BigDecimal money(
            BigDecimal value,
            String field,
            boolean allowZero) {
        BigDecimal result = Objects.requireNonNull(value, field);
        if ((allowZero && result.signum() < 0) || (!allowZero && result.signum() <= 0)) {
            throw new IllegalArgumentException(
                    field + (allowZero ? " must not be negative" : " must be positive"));
        }
        int effectivePrecision = result.precision() - Math.min(0, result.scale());
        if (effectivePrecision > MAX_MONEY_PRECISION
                || Math.max(0, result.scale()) > MAX_MONEY_SCALE) {
            throw new IllegalArgumentException(
                    field + " must have at most " + MAX_MONEY_PRECISION
                            + " digits and " + MAX_MONEY_SCALE + " decimal places");
        }
        return result;
    }
}
