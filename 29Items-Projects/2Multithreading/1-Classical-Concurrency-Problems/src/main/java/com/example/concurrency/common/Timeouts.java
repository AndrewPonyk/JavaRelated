package com.example.concurrency.common;

import java.time.Duration;
import java.util.Objects;

/** Shared validation and overflow-safe conversion for public timeout parameters. */
public final class Timeouts {
    private Timeouts() {
    }

    public static long toNanos(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        try {
            return timeout.toNanos();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
