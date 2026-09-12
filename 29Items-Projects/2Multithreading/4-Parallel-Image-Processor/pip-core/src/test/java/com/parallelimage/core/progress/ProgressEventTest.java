package com.parallelimage.core.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.core.progress.ProgressEvent.Phase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** {@link ProgressEvent} tests. */
class ProgressEventTest {

    @Nested
    @DisplayName("canonical constructor validation")
    class Validation {

        @Test
        @DisplayName("rejects a blank batchId")
        void rejectsBlankBatchId() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ProgressEvent(" ", "job-1", Phase.JOB_STARTED, 0, 1, "x"));
        }

        @Test
        @DisplayName("rejects a null phase")
        void rejectsNullPhase() {
            assertThrows(NullPointerException.class,
                    () -> new ProgressEvent("batch-1", "job-1", null, 0, 1, "x"));
        }

        @Test
        @DisplayName("rejects a negative completed count")
        void rejectsNegativeCompleted() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ProgressEvent("batch-1", "job-1", Phase.JOB_STARTED, -1, 1, "x"));
        }

        @Test
        @DisplayName("rejects a negative total")
        void rejectsNegativeTotal() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ProgressEvent("batch-1", "job-1", Phase.JOB_STARTED, 0, -1, "x"));
        }

        @Test
        @DisplayName("allows a null jobId for batch-level events")
        void allowsNullJobId() {
            ProgressEvent event = new ProgressEvent("batch-1", null, Phase.BATCH_STARTED, 0, 1, "x");
            assertNull(event.jobId());
        }

        @Test
        @DisplayName("defaults a null detail to an empty string")
        void nullDetailBecomesEmptyString() {
            ProgressEvent event = new ProgressEvent("batch-1", "job-1", Phase.JOB_STARTED, 0, 1, null);
            assertEquals("", event.detail());
        }
    }

    @Nested
    @DisplayName("factory methods")
    class Factories {

        @Test
        @DisplayName("batchStarted() produces a BATCH_STARTED event with zero completed")
        void batchStartedProducesExpectedShape() {
            ProgressEvent event = ProgressEvent.batchStarted("batch-1", 10);
            assertEquals("batch-1", event.batchId());
            assertNull(event.jobId());
            assertEquals(Phase.BATCH_STARTED, event.phase());
            assertEquals(0, event.completed());
            assertEquals(10, event.total());
            assertEquals("batch started", event.detail());
        }

        @Test
        @DisplayName("batchFinished() produces a BATCH_FINISHED event carrying the final counts")
        void batchFinishedProducesExpectedShape() {
            ProgressEvent event = ProgressEvent.batchFinished("batch-1", 7, 10);
            assertEquals("batch-1", event.batchId());
            assertNull(event.jobId());
            assertEquals(Phase.BATCH_FINISHED, event.phase());
            assertEquals(7, event.completed());
            assertEquals(10, event.total());
            assertEquals("done", event.detail());
        }
    }

    @Nested
    @DisplayName("fraction()")
    class Fraction {

        @Test
        @DisplayName("is zero when total is zero")
        void zeroWhenTotalIsZero() {
            ProgressEvent event = new ProgressEvent("batch-1", null, Phase.BATCH_STARTED, 0, 0, "x");
            assertEquals(0.0d, event.fraction(), 1e-9);
        }

        @Test
        @DisplayName("divides completed by total, not the other way around")
        void dividesCompletedByTotal() {
            ProgressEvent event = new ProgressEvent("batch-1", null, Phase.JOB_STARTED, 1, 4, "x");
            assertEquals(0.25d, event.fraction(), 1e-9);
        }

        @Test
        @DisplayName("clamps to 1.0 rather than exceeding it when completed somehow overshoots total")
        void clampsToOneWhenCompletedExceedsTotal() {
            ProgressEvent event = new ProgressEvent("batch-1", null, Phase.JOB_STARTED, 5, 4, "x");
            assertEquals(1.0d, event.fraction(), 1e-9);
        }

        @Test
        @DisplayName("is exactly 1.0, not clamped short, when completed equals total")
        void isOneWhenCompletedEqualsTotal() {
            ProgressEvent event = new ProgressEvent("batch-1", null, Phase.JOB_COMPLETED, 4, 4, "x");
            assertEquals(1.0d, event.fraction(), 1e-9);
        }
    }

    @Nested
    @DisplayName("isBatchLevel()")
    class IsBatchLevel {

        @ParameterizedTest(name = "{0} is batch-level")
        @EnumSource(value = Phase.class, names = {"BATCH_STARTED", "BATCH_FINISHED"})
        void batchPhasesAreBatchLevel(Phase phase) {
            ProgressEvent event = new ProgressEvent("batch-1", null, phase, 0, 1, "x");
            assertTrue(event.isBatchLevel());
        }

        @ParameterizedTest(name = "{0} is not batch-level")
        @EnumSource(value = Phase.class,
                names = {"JOB_STARTED", "JOB_COMPLETED", "JOB_FAILED", "JOB_CANCELLED"})
        void jobPhasesAreNotBatchLevel(Phase phase) {
            ProgressEvent event = new ProgressEvent("batch-1", "job-1", phase, 0, 1, "x");
            assertFalse(event.isBatchLevel());
        }
    }
}
