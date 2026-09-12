package com.parallelimage.core.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.core.port.JobRepository.BatchSummary;
import com.parallelimage.core.port.JobRepository.JobRecord;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** {@link JobRepository} tests: the {@link JobRecord}/{@link BatchSummary} helpers and {@code NO_OP}. */
class JobRepositoryTest {

    @Nested
    @DisplayName("JobRecord.failed")
    class Failed {

        @Test
        @DisplayName("true when status is FAILED")
        void trueWhenFailed() {
            JobRecord record = new JobRecord("job-1", "batch-1", "src.png", "out.png",
                    com.parallelimage.core.model.JobStatus.FAILED, 10L, 0L, "boom", Instant.EPOCH);
            assertTrue(record.failed());
        }

        @Test
        @DisplayName("false for every other status")
        void falseOtherwise() {
            JobRecord record = new JobRecord("job-1", "batch-1", "src.png", "out.png",
                    com.parallelimage.core.model.JobStatus.COMPLETED, 10L, 100L, null, Instant.EPOCH);
            assertEquals(false, record.failed());
        }
    }

    @Nested
    @DisplayName("BatchSummary.successRate")
    class SuccessRate {

        @Test
        @DisplayName("is succeeded / total")
        void isSucceededOverTotal() {
            BatchSummary summary = new BatchSummary("batch-1", 4, 3, 1, 0, 1_000L, Instant.EPOCH);
            assertEquals(0.75d, summary.successRate());
        }

        @Test
        @DisplayName("is 0.0 for an empty batch, not a division by zero")
        void isZeroForEmptyBatch() {
            BatchSummary summary = new BatchSummary("batch-1", 0, 0, 0, 0, 0L, Instant.EPOCH);
            assertEquals(0.0d, summary.successRate());
        }
    }

    @Nested
    @DisplayName("NO_OP")
    class NoOp {

        @Test
        @DisplayName("every read returns empty and every write is silently discarded")
        void readsAreEmptyWritesAreDiscarded() {
            JobRepository repo = JobRepository.NO_OP;

            repo.saveBatch("b", List.of());
            repo.recordMetadata(null);
            repo.completeBatch("b", 0L);

            assertEquals(List.of(), repo.recentJobs(10));
            assertEquals(List.of(), repo.recentBatches(10));
            assertEquals(java.util.Optional.empty(), repo.findBatch("b"));
            assertEquals(0, repo.purgeOlderThan(Instant.now()));
        }

        @Test
        @DisplayName("recordOutcome does not throw")
        void recordOutcomeDoesNotThrow() {
            JobRepository.NO_OP.recordOutcome(new com.parallelimage.core.model.JobOutcome.Cancelled("job-1", 0L));
        }

        @Test
        @DisplayName("close() does not throw")
        void closeDoesNotThrow() {
            JobRepository.NO_OP.close();
        }

        @Test
        @DisplayName("toString identifies it as the no-op implementation")
        void toStringIdentifiesItself() {
            assertEquals("JobRepository.NO_OP", JobRepository.NO_OP.toString());
        }
    }
}
