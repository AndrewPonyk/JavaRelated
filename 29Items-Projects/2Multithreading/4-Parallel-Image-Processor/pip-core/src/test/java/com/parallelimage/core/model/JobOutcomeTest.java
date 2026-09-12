package com.parallelimage.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** {@link JobOutcome} tests. */
class JobOutcomeTest {

    @Nested
    @DisplayName("Success")
    class SuccessTest {

        @Test
        @DisplayName("toStatus() maps to COMPLETED")
        void mapsToCompleted() {
            JobOutcome outcome = new JobOutcome.Success("job-1", Path.of("out.png"), 100L, 42L);
            assertEquals(JobStatus.COMPLETED, outcome.toStatus());
        }

        @Test
        @DisplayName("rejects a blank jobId")
        void rejectsBlankJobId() {
            assertThrows(IllegalArgumentException.class,
                    () -> new JobOutcome.Success(" ", Path.of("out.png"), 0L, 0L));
        }

        @Test
        @DisplayName("rejects a negative duration")
        void rejectsNegativeDuration() {
            assertThrows(IllegalArgumentException.class,
                    () -> new JobOutcome.Success("job-1", Path.of("out.png"), -1L, 0L));
        }

        @Test
        @DisplayName("rejects a negative pixel count")
        void rejectsNegativePixelCount() {
            assertThrows(IllegalArgumentException.class,
                    () -> new JobOutcome.Success("job-1", Path.of("out.png"), 0L, -1L));
        }
    }

    @Nested
    @DisplayName("Failure")
    class FailureTest {

        @Test
        @DisplayName("toStatus() maps to FAILED")
        void mapsToFailed() {
            JobOutcome outcome = new JobOutcome.Failure("job-1", "bad file", "java.io.IOException",
                    "decode", 50L);
            assertEquals(JobStatus.FAILED, outcome.toStatus());
        }

        @Test
        @DisplayName("rejects a blank reason")
        void rejectsBlankReason() {
            assertThrows(IllegalArgumentException.class,
                    () -> new JobOutcome.Failure("job-1", " ", "java.io.IOException", "decode", 0L));
        }

        @Test
        @DisplayName("from() uses the throwable's message when present")
        void fromUsesThrowableMessage() {
            Exception cause = new IllegalStateException("disk full");

            JobOutcome.Failure failure = JobOutcome.Failure.from("job-1", cause, "write", 10L);

            assertEquals("disk full", failure.reason());
            assertEquals("java.lang.IllegalStateException", failure.exceptionType());
            assertEquals("write", failure.operationName());
            assertEquals(10L, failure.durationNanos());
        }

        @Test
        @DisplayName("from() falls back to the exception's simple name when the message is null")
        void fromFallsBackToSimpleNameWhenMessageIsNull() {
            Exception cause = new NullPointerException();

            JobOutcome.Failure failure = JobOutcome.Failure.from("job-1", cause, "decode", 5L);

            assertEquals("NullPointerException", failure.reason());
        }

        @Test
        @DisplayName("from() falls back to the exception's simple name when the message is blank")
        void fromFallsBackToSimpleNameWhenMessageIsBlank() {
            Exception cause = new IllegalStateException("   ");

            JobOutcome.Failure failure = JobOutcome.Failure.from("job-1", cause, "decode", 5L);

            assertEquals("IllegalStateException", failure.reason());
        }
    }

    @Nested
    @DisplayName("Cancelled")
    class CancelledTest {

        @Test
        @DisplayName("toStatus() maps to CANCELLED")
        void mapsToCancelled() {
            JobOutcome outcome = new JobOutcome.Cancelled("job-1", 5L);
            assertEquals(JobStatus.CANCELLED, outcome.toStatus());
        }

        @Test
        @DisplayName("rejects a blank jobId")
        void rejectsBlankJobId() {
            assertThrows(IllegalArgumentException.class, () -> new JobOutcome.Cancelled("", 0L));
        }
    }
}
