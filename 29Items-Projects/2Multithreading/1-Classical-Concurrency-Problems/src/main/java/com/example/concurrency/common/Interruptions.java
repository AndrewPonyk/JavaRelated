package com.example.concurrency.common;

/** Utilities for interruption at non-throwing application boundaries. */
public final class Interruptions {
    private Interruptions() {
    }

    /** Restores the interrupt flag and returns an exception suitable for a CLI boundary. */
    public static IllegalStateException restoreAndWrap(String operation, InterruptedException cause) {
        Thread.currentThread().interrupt();
        return new IllegalStateException(operation + " was interrupted", cause);
    }
}
