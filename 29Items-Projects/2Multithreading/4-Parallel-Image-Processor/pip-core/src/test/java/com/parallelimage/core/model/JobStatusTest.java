package com.parallelimage.core.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Exhaustive pairwise coverage of the state machine: every legal transition in the diagram in
 * {@link JobStatus}'s javadoc must return {@code true}, and every other pair — including
 * self-transitions and both terminal states — must return {@code false}.
 */
class JobStatusTest {

    @ParameterizedTest(name = "{0} is terminal")
    @EnumSource(value = JobStatus.class, names = {"COMPLETED", "FAILED", "CANCELLED"})
    @DisplayName("isTerminal() is true for COMPLETED, FAILED, CANCELLED")
    void terminalStatusesReportTerminal(JobStatus status) {
        assertTrue(status.isTerminal());
    }

    @ParameterizedTest(name = "{0} is not terminal")
    @EnumSource(value = JobStatus.class, names = {"PENDING", "RUNNING"})
    @DisplayName("isTerminal() is false for PENDING, RUNNING")
    void nonTerminalStatusesReportNotTerminal(JobStatus status) {
        assertFalse(status.isTerminal());
    }

    @ParameterizedTest(name = "{0} -> {1} is legal")
    @CsvSource({
            "PENDING, RUNNING",
            "PENDING, CANCELLED",
            "RUNNING, COMPLETED",
            "RUNNING, FAILED",
            "RUNNING, CANCELLED",
            "FAILED, PENDING"
    })
    @DisplayName("canTransitionTo() allows every edge drawn in the lifecycle diagram")
    void legalTransitionsAreAllowed(JobStatus from, JobStatus to) {
        assertTrue(from.canTransitionTo(to), from + " -> " + to + " should be legal");
    }

    @ParameterizedTest(name = "{0} -> {1} is illegal")
    @CsvSource({
            "PENDING, PENDING",
            "PENDING, COMPLETED",
            "PENDING, FAILED",
            "RUNNING, PENDING",
            "RUNNING, RUNNING",
            "FAILED, RUNNING",
            "FAILED, COMPLETED",
            "FAILED, CANCELLED",
            "FAILED, FAILED",
            "COMPLETED, PENDING",
            "COMPLETED, RUNNING",
            "COMPLETED, COMPLETED",
            "COMPLETED, FAILED",
            "COMPLETED, CANCELLED",
            "CANCELLED, PENDING",
            "CANCELLED, RUNNING",
            "CANCELLED, COMPLETED",
            "CANCELLED, FAILED",
            "CANCELLED, CANCELLED"
    })
    @DisplayName("canTransitionTo() rejects every non-edge, including self-loops and terminal escapes")
    void illegalTransitionsAreRejected(JobStatus from, JobStatus to) {
        assertFalse(from.canTransitionTo(to), from + " -> " + to + " should be illegal");
    }
}
