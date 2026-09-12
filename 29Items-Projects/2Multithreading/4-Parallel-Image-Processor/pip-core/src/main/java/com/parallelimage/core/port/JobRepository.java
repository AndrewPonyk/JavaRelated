package com.parallelimage.core.port;

import com.parallelimage.core.model.ImageJob;
import com.parallelimage.core.model.ImageMetadata;
import com.parallelimage.core.model.JobOutcome;
import com.parallelimage.core.model.JobStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port for durable job history. Implemented by {@code pip-persistence} over SQLite.
 *
 * <h2>Why a port and not a DAO called directly</h2>
 * {@code pip-core} has an <em>empty</em> {@code <dependencies>} block — the hexagonal boundary is
 * enforced by the build, not by convention (see {@code pip-core/pom.xml}). Declaring the contract here
 * as pure-JDK types and letting the adapter live in another module means the processing engine can be
 * unit-tested with {@link #NO_OP} and no database at all, and that swapping SQLite for Postgres or for
 * a JSON file is a change in exactly one module.
 *
 * <h2>Implementation contract</h2>
 * <ul>
 *   <li><b>Thread-safe.</b> Called concurrently from fork/join workers.</li>
 *   <li><b>Never throws into the engine.</b> History is a nice-to-have; a locked database must not
 *       fail a batch that has already produced correct output files. Adapters log and swallow.
 *       That is a deliberate, documented asymmetry with the rest of the codebase (ARCHITECTURE
 *       §2.6 rule 6).</li>
 *   <li><b>Cheap per call.</b> A worker calls {@link #recordOutcome} between images; the adapter is
 *       expected to buffer and batch its own transactions rather than committing once per row
 *       (TECH-NOTES §3.6 G3).</li>
 * </ul>
 */
public interface JobRepository extends AutoCloseable {

    /** Read model for the UI's history table. */
    record JobRecord(
            String jobId,
            String batchId,
            String sourcePath,
            String targetPath,
            JobStatus status,
            long durationMillis,
            long pixelsProcessed,
            String failureReason,
            Instant recordedAt) {

        public boolean failed() {
            return status == JobStatus.FAILED;
        }
    }

    /** Aggregate row for the batch history list. */
    record BatchSummary(
            String batchId,
            int total,
            int succeeded,
            int failed,
            int cancelled,
            long wallClockMillis,
            Instant startedAt) {

        public double successRate() {
            return total == 0 ? 0.0d : succeeded / (double) total;
        }
    }

    /** Persists the planned jobs of a batch in {@link JobStatus#PENDING}. */
    void saveBatch(String batchId, List<ImageJob> jobs);

    /** Records the terminal outcome of one job. Called once per job, from a worker thread. */
    void recordOutcome(JobOutcome outcome);

    /**
     * Marks a job {@link JobStatus#RUNNING}, right before its actual work starts.
     *
     * <p>This is what lets a restart tell "a job the previous run never got to" (still
     * {@code PENDING}) apart from "a job the previous run crashed in the middle of" (stuck
     * {@code RUNNING} forever, since nothing ever calls {@link #recordOutcome} for it again).
     */
    void markRunning(String jobId);

    /** Every job currently in {@code status}, across all batches. */
    List<JobRecord> findByStatus(JobStatus status);

    /** Records decoded image facts. Separate from the outcome because it is known earlier. */
    void recordMetadata(ImageMetadata metadata);

    /** Marks a batch finished and stores its wall-clock duration. */
    void completeBatch(String batchId, long wallClockMillis);

    /** Most recent jobs, newest first. */
    List<JobRecord> recentJobs(int limit);

    /** Most recent batches, newest first. */
    List<BatchSummary> recentBatches(int limit);

    Optional<BatchSummary> findBatch(String batchId);

    /**
     * Deletes every batch (and, via {@code ON DELETE CASCADE}, its jobs and image metadata) that
     * finished before {@code cutoff}. A batch still running or never completed is never purged —
     * only rows with a non-null {@code finished_at} are eligible.
     *
     * @return the number of batches deleted, for the caller to log
     */
    int purgeOlderThan(Instant cutoff);

    /** Overridden to drop the checked exception: callers should not need a try/catch to shut down. */
    @Override
    void close();

    /**
     * Discards everything.
     *
     * <p>Not a test double — the CLI uses it for {@code --no-history}, and the UI falls back to it when
     * the database cannot be opened, which is what lets the application start read-only on a
     * network share rather than refusing to run.
     */
    JobRepository NO_OP = new JobRepository() {

        @Override
        public void saveBatch(String batchId, List<ImageJob> jobs) {
            // intentionally empty
        }

        @Override
        public void recordOutcome(JobOutcome outcome) {
            // intentionally empty
        }

        @Override
        public void markRunning(String jobId) {
            // intentionally empty
        }

        @Override
        public List<JobRecord> findByStatus(JobStatus status) {
            return List.of();
        }

        @Override
        public void recordMetadata(ImageMetadata metadata) {
            // intentionally empty
        }

        @Override
        public void completeBatch(String batchId, long wallClockMillis) {
            // intentionally empty
        }

        @Override
        public List<JobRecord> recentJobs(int limit) {
            return List.of();
        }

        @Override
        public List<BatchSummary> recentBatches(int limit) {
            return List.of();
        }

        @Override
        public Optional<BatchSummary> findBatch(String batchId) {
            return Optional.empty();
        }

        @Override
        public int purgeOlderThan(Instant cutoff) {
            return 0;
        }

        @Override
        public void close() {
            // intentionally empty
        }

        @Override
        public String toString() {
            return "JobRepository.NO_OP";
        }
    };
}
