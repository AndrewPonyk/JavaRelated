package com.parallelimage.core.model;

/**
 * Lifecycle of a single {@link ImageJob}.
 *
 * <pre>
 *   PENDING ──▶ RUNNING ──▶ COMPLETED
 *                  │
 *                  ├──▶ FAILED ──▶ PENDING   (user retry)
 *                  └──▶ CANCELLED
 * </pre>
 *
 * <p>Persisted as the enum <em>name</em> (not the ordinal) so reordering this enum can never
 * silently reinterpret rows already in SQLite.
 */
public enum JobStatus {

    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    /** {@code true} when no further processing will occur without explicit user action. */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    /** Guards illegal transitions before they reach the repository. */
    public boolean canTransitionTo(JobStatus next) {
        return switch (this) {
            case PENDING -> next == RUNNING || next == CANCELLED;
            case RUNNING -> next == COMPLETED || next == FAILED || next == CANCELLED;
            case FAILED -> next == PENDING;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
