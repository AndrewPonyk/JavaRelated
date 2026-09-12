package com.parallelimage.persistence.repository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.core.model.ImageJob;
import com.parallelimage.core.model.ImageMetadata;
import com.parallelimage.core.model.JobOutcome;
import com.parallelimage.core.model.JobStatus;
import com.parallelimage.core.model.ProcessingOptions;
import com.parallelimage.core.pipeline.ImageOperation;
import com.parallelimage.core.pipeline.PipelineFormat;
import com.parallelimage.core.port.JobRepository.BatchSummary;
import com.parallelimage.core.port.JobRepository.JobRecord;
import com.parallelimage.persistence.jdbc.Database;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link SqliteJobRepository} tests against a real SQLite file under {@code @TempDir}.
 *
 * <p>Two properties make this suite worth its runtime, and neither can be tested with a mock:
 * <ul>
 *   <li><strong>The writes are asynchronous.</strong> Every assertion here goes through
 *       {@link SqliteJobRepository#flush()} rather than a sleep, which is also the only way the
 *       write-then-read tests are not flaky on a loaded machine.</li>
 *   <li><strong>The reads go through views.</strong> {@code v_recent_jobs} and
 *       {@code v_batch_summary} are shipped SQL, so a wrong column name or a JOIN that hides skipped
 *       jobs is a defect in the adapter that only a real database will show.</li>
 * </ul>
 *
 * <p>{@code :memory:} is deliberately not used: it cannot do WAL, and it would make
 * {@link #readsStillWorkAfterClose()} pass for the wrong reason.
 */
class SqliteJobRepositoryTest {

    private static final List<ImageOperation> PIPELINE = List.of(
            new ImageOperation.Grayscale(),
            new ImageOperation.Resize(1024, 768, true),
            new ImageOperation.Sharpen(1.5d));

    @TempDir
    Path tempDir;

    private Database database;
    private SqliteJobRepository repository;

    @BeforeEach
    void openRepository() {
        database = Database.at(tempDir.resolve("pip.db"));
        repository = SqliteJobRepository.open(database, () -> 4);
    }

    @AfterEach
    void closeRepository() {
        repository.close();
    }

    // ------------------------------------------------------------------------
    //  The round trip
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("a batch survives save, three kinds of outcome, and completion")
    void roundTripsAWholeBatch() {
        String batchId = "batch-round-trip";
        ImageJob ok = job(batchId, "one.jpg");
        ImageJob broken = job(batchId, "two.jpg");
        ImageJob abandoned = job(batchId, "three.jpg");

        repository.saveBatch(batchId, List.of(ok, broken, abandoned));
        repository.recordMetadata(new ImageMetadata(ok.id(), 4000, 3000, "JPEG", 2_500_000L,
                BufferedImage.TYPE_INT_RGB, false, Map.of("Model", "NIKON D850")));
        repository.recordOutcome(
                new JobOutcome.Success(ok.id(), ok.target(), 250_000_000L, 12_000_000L));
        repository.recordOutcome(JobOutcome.Failure.from(broken.id(),
                new java.io.IOException("truncated JPEG stream"), "decode", 5_000_000L));
        repository.recordOutcome(new JobOutcome.Cancelled(abandoned.id(), 1_000_000L));
        repository.completeBatch(batchId, 900L);

        assertTrue(repository.flush(), "the writer thread should drain within the flush timeout");
        assertEquals(0L, repository.droppedWrites(), "nothing should have been dropped");

        List<JobRecord> jobs = repository.recentJobs(10);
        assertEquals(3, jobs.size(), () -> "expected all three jobs, got " + jobs);
        Map<String, JobRecord> byId = new LinkedHashMap<>();
        jobs.forEach(record -> byId.put(record.jobId(), record));

        JobRecord completed = byId.get(ok.id());
        assertEquals(JobStatus.COMPLETED, completed.status());
        assertEquals(250L, completed.durationMillis(), "nanos are stored as millis");
        assertEquals(12_000_000L, completed.pixelsProcessed());
        assertNull(completed.failureReason(), "a success has no reason");
        assertEquals(ok.source().toString(), completed.sourcePath());
        assertEquals(batchId, completed.batchId());
        assertNotNull(completed.recordedAt(), "recorded_at is set by recordOutcome");

        JobRecord failed = byId.get(broken.id());
        assertTrue(failed.failed());
        assertEquals("truncated JPEG stream", failed.failureReason());

        assertEquals(JobStatus.CANCELLED, byId.get(abandoned.id()).status());

        BatchSummary summary = repository.findBatch(batchId).orElseThrow();
        assertEquals(3, summary.total());
        assertEquals(1, summary.succeeded());
        assertEquals(1, summary.failed());
        assertEquals(1, summary.cancelled());
        assertEquals(900L, summary.wallClockMillis());
        assertNotNull(summary.startedAt());
        assertEquals(1 / 3.0d, summary.successRate(), 1e-9);

        assertEquals(List.of(summary), repository.recentBatches(10));
    }

    @Test
    @DisplayName("a job whose target already existed keeps its row even with no metadata")
    void jobsWithoutMetadataAreStillListed() {
        // v_recent_jobs LEFT JOINs image_metadata. An INNER JOIN would hide exactly the skipped jobs,
        // and "the history is missing the files it skipped" is a bug report nobody would connect to a
        // JOIN keyword.
        String batchId = "batch-no-metadata";
        ImageJob skipped = job(batchId, "already-there.png");
        repository.saveBatch(batchId, List.of(skipped));
        repository.recordOutcome(
                new JobOutcome.Success(skipped.id(), skipped.target(), 0L, 0L));
        assertTrue(repository.flush());

        assertEquals(1, repository.recentJobs(10).size());
    }

    @Test
    @DisplayName("a saved but never-finished job reads back as PENDING with no timestamp")
    void pendingJobsHaveNoRecordedAt() {
        String batchId = "batch-unfinished";
        ImageJob queued = job(batchId, "waiting.tif");
        repository.saveBatch(batchId, List.of(queued));
        assertTrue(repository.flush());

        JobRecord record = repository.recentJobs(10).get(0);
        assertEquals(JobStatus.PENDING, record.status());
        assertNull(record.recordedAt(), "SQL NULL must survive as null, not as epoch 0");
    }

    // ------------------------------------------------------------------------
    //  The batch snapshot columns
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("the batch snapshot is taken from the jobs' own options")
    void storesTheBatchSnapshot() throws SQLException {
        String batchId = "batch-snapshot";
        repository.saveBatch(batchId, List.of(job(batchId, "a.png")));
        assertTrue(repository.flush());

        try (Connection connection = database.open();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT output_format, quality, pipeline, tile_threshold_px, batch_threshold,"
                                + " parallelism, finished_at FROM batches WHERE batch_id = '"
                                + batchId + "'")) {
            assertTrue(rs.next(), "the batches row should exist");
            assertEquals("jpg", rs.getString("output_format"), "jpeg is canonicalised to jpg");
            assertEquals(0.8d, rs.getDouble("quality"), 1e-6);
            // Pinned against PipelineFormat rather than a literal: the point is that what is stored is
            // what --pipeline would parse back, not that it happens to be this string today.
            assertEquals(PipelineFormat.render(PIPELINE), rs.getString("pipeline"));
            assertEquals(4096L, rs.getLong("tile_threshold_px"));
            assertEquals(16, rs.getInt("batch_threshold"));
            assertEquals(4, rs.getInt("parallelism"), "supplied by the injected IntSupplier");
            rs.getLong("finished_at");
            assertTrue(rs.wasNull(), "an incomplete batch has no finished_at");
        }
    }

    @Test
    @DisplayName("the stored pipeline parses back to the operations that were run")
    void thePipelineColumnRoundTrips() throws SQLException {
        String batchId = "batch-pipeline";
        repository.saveBatch(batchId, List.of(job(batchId, "b.png")));
        assertTrue(repository.flush());

        String stored = string("SELECT pipeline FROM batches WHERE batch_id = '" + batchId + "'");
        assertEquals(PIPELINE, PipelineFormat.parse(stored),
                "history that cannot be replayed is decoration");
    }

    @Test
    @DisplayName("saving the same batch twice does not duplicate rows or reset the batch")
    void saveBatchIsIdempotent() throws SQLException {
        String batchId = "batch-twice";
        List<ImageJob> jobs = List.of(job(batchId, "c.png"), job(batchId, "d.png"));
        repository.saveBatch(batchId, jobs);
        repository.saveBatch(batchId, jobs);
        assertTrue(repository.flush());

        assertEquals(1, count("SELECT COUNT(*) FROM batches WHERE batch_id = '" + batchId + "'"));
        assertEquals(2, count("SELECT COUNT(*) FROM jobs WHERE batch_id = '" + batchId + "'"));
    }

    @Test
    @DisplayName("a null or empty batch is ignored rather than written as an empty batch")
    void ignoresNothingToSave() {
        repository.saveBatch("batch-empty", List.of());
        repository.saveBatch(null, List.of(job("x", "e.png")));
        assertTrue(repository.flush());

        assertEquals(0, count("SELECT COUNT(*) FROM batches"));
    }

    // ------------------------------------------------------------------------
    //  Metadata and its JSON
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("metadata is upserted, so a second decode replaces rather than fails")
    void metadataIsUpserted() throws SQLException {
        String batchId = "batch-metadata";
        ImageJob only = job(batchId, "f.png");
        repository.saveBatch(batchId, List.of(only));
        repository.recordMetadata(new ImageMetadata(only.id(), 100, 50, "PNG", 1234L,
                BufferedImage.TYPE_INT_ARGB, true, Map.of()));
        repository.recordMetadata(new ImageMetadata(only.id(), 200, 100, "PNG", 1234L,
                BufferedImage.TYPE_INT_ARGB, true, Map.of()));
        assertTrue(repository.flush());

        assertEquals(1, count("SELECT COUNT(*) FROM image_metadata WHERE job_id = '"
                + only.id() + "'"));
        assertEquals(200, count("SELECT width FROM image_metadata WHERE job_id = '"
                + only.id() + "'"));
        assertEquals(1, count("SELECT has_alpha FROM image_metadata WHERE job_id = '"
                + only.id() + "'"), "STRICT has no BOOLEAN; alpha is 1 or 0");
    }

    @Test
    @DisplayName("EXIF values that contain quotes and newlines are escaped, not corrupted")
    void exifIsEscaped() throws SQLException {
        Map<String, String> hostile = new LinkedHashMap<>();
        hostile.put("Artist", "A \"quoted\" name");
        hostile.put("Comment", "line one\nline two\ttabbed");
        hostile.put("Path", "C:\\photos\\raw");

        assertEquals("{\"Artist\":\"A \\\"quoted\\\" name\","
                + "\"Comment\":\"line one\\nline two\\ttabbed\","
                + "\"Path\":\"C:\\\\photos\\\\raw\"}",
                SqliteJobRepository.toJsonObject(hostile));

        // And the same values through the database, because a correct string that a PreparedStatement
        // then mangles would be no better. Compared against the record's own copy of the map rather
        // than against the literal above: ImageMetadata canonicalises exif with Map.copyOf, whose
        // iteration order is unspecified, so pinning the key order here would be a flaky test.
        String batchId = "batch-exif";
        ImageJob only = job(batchId, "g.jpg");
        ImageMetadata metadata = new ImageMetadata(only.id(), 10, 10, "JPEG", 1L,
                BufferedImage.TYPE_INT_RGB, false, hostile);
        repository.saveBatch(batchId, List.of(only));
        repository.recordMetadata(metadata);
        assertTrue(repository.flush());

        String stored = string("SELECT exif_json FROM image_metadata WHERE job_id = '"
                + only.id() + "'");
        assertEquals(SqliteJobRepository.toJsonObject(metadata.exif()), stored);
        assertTrue(stored.contains("C:\\\\photos\\\\raw"),
                "the backslashes must arrive doubled, not eaten: " + stored);
        assertTrue(stored.contains("line one\\nline two"), stored);
    }

    @Test
    @DisplayName("an empty EXIF map is an empty JSON object, not null")
    void emptyExifIsAnEmptyObject() {
        assertEquals("{}", SqliteJobRepository.toJsonObject(Map.of()));
        assertEquals("{}", SqliteJobRepository.toJsonObject(null));
    }

    // ------------------------------------------------------------------------
    //  The failure columns
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("a failure keeps the exception type and the operation that was running")
    void failureKeepsItsDiagnostics() throws SQLException {
        String batchId = "batch-failure";
        ImageJob broken = job(batchId, "h.jpg");
        repository.saveBatch(batchId, List.of(broken));
        repository.recordOutcome(JobOutcome.Failure.from(broken.id(),
                new OutOfMemoryError("Java heap space"), "resize", 7_000_000L));
        assertTrue(repository.flush());

        assertEquals("java.lang.OutOfMemoryError",
                string("SELECT exception_type FROM jobs WHERE job_id = '" + broken.id() + "'"));
        assertEquals("resize",
                string("SELECT operation_name FROM jobs WHERE job_id = '" + broken.id() + "'"));
        assertEquals("Java heap space",
                string("SELECT failure_reason FROM jobs WHERE job_id = '" + broken.id() + "'"));
    }

    @Test
    @DisplayName("an outcome for a job nobody saved is discarded without failing the batch")
    void unknownOutcomesAreIgnored() {
        repository.recordOutcome(new JobOutcome.Cancelled(UUID.randomUUID().toString(), 0L));
        repository.recordOutcome(null);

        assertTrue(repository.flush(), "a 0-row UPDATE must not wedge the writer thread");
        assertEquals(0, count("SELECT COUNT(*) FROM jobs"));
    }

    // ------------------------------------------------------------------------
    //  completeBatch and the drift view
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("after completeBatch the stored counters agree with the rows")
    void countersDoNotDrift() {
        String batchId = "batch-drift";
        List<ImageJob> jobs = jobs(batchId, 7);
        repository.saveBatch(batchId, jobs);
        for (ImageJob each : jobs) {
            repository.recordOutcome(new JobOutcome.Success(each.id(), each.target(), 1_000_000L, 10L));
        }
        repository.completeBatch(batchId, 42L);
        assertTrue(repository.flush());

        assertEquals(0, count("SELECT COUNT(*) FROM v_batch_counter_drift"),
                "v_batch_counter_drift returning rows means the write path lost an update");
        assertEquals(7, repository.findBatch(batchId).orElseThrow().succeeded());
    }

    @Test
    @DisplayName("a batch that never completes is reported as drifting, which is the honest state")
    void anAbandonedBatchDrifts() {
        // Not a defect being asserted — the opposite. A crash between the last outcome and
        // completeBatch is exactly what the view is for, so it must actually notice.
        String batchId = "batch-crashed";
        List<ImageJob> jobs = jobs(batchId, 3);
        repository.saveBatch(batchId, jobs);
        jobs.forEach(each -> repository.recordOutcome(
                new JobOutcome.Success(each.id(), each.target(), 0L, 0L)));
        assertTrue(repository.flush());

        assertEquals(1, count("SELECT COUNT(*) FROM v_batch_counter_drift"),
                "succeeded is still 0 on batches while three jobs are COMPLETED");
    }

    @Test
    @DisplayName("completeBatch never records a finish before the start")
    void finishedAtIsNeverBeforeStartedAt() throws SQLException {
        String batchId = "batch-clock";
        repository.saveBatch(batchId, List.of(job(batchId, "i.png")));
        repository.completeBatch(batchId, -5L);
        assertTrue(repository.flush());

        // The CHECK constraint would have rejected the row otherwise; asserting it here says why the
        // MAX(?, started_at) in the UPDATE is not decoration. A clock stepped backwards by NTP is the
        // realistic cause.
        try (Connection connection = database.open();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT started_at, finished_at, wall_clock_ms"
                        + " FROM batches WHERE batch_id = '" + batchId + "'")) {
            assertTrue(rs.next());
            assertTrue(rs.getLong("finished_at") >= rs.getLong("started_at"));
            assertEquals(0L, rs.getLong("wall_clock_ms"), "a negative duration is clamped to 0");
        }
    }

    // ------------------------------------------------------------------------
    //  Reads
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("recentJobs honours its limit and returns the newest first")
    void recentJobsIsOrderedAndLimited() {
        String batchId = "batch-order";
        List<ImageJob> jobs = jobs(batchId, 5);
        repository.saveBatch(batchId, jobs);
        for (ImageJob each : jobs) {
            repository.recordOutcome(new JobOutcome.Success(each.id(), each.target(), 0L, 0L));
            // recorded_at has millisecond resolution, so without a nudge the order would be arbitrary
            // and this test would assert nothing. Flushing between writes is enough on any real clock.
            assertTrue(repository.flush());
        }

        List<JobRecord> newest = repository.recentJobs(2);
        assertEquals(2, newest.size());
        assertTrue(newest.get(0).recordedAt().compareTo(newest.get(1).recordedAt()) >= 0,
                "newest first: " + newest);

        assertEquals(List.of(), repository.recentJobs(0));
        assertEquals(List.of(), repository.recentBatches(-1));
    }

    @Test
    @DisplayName("an unknown or blank batch id is an empty Optional, not an exception")
    void findBatchOfAnUnknownIdIsEmpty() {
        assertEquals(Optional.empty(), repository.findBatch("no-such-batch"));
        assertEquals(Optional.empty(), repository.findBatch(""));
        assertEquals(Optional.empty(), repository.findBatch(null));
    }

    @Test
    @DisplayName("reads still work after close, because they do not use the writer's connection")
    void readsStillWorkAfterClose() {
        String batchId = "batch-after-close";
        ImageJob only = job(batchId, "j.png");
        repository.saveBatch(batchId, List.of(only));
        repository.recordOutcome(new JobOutcome.Success(only.id(), only.target(), 0L, 1L));
        repository.completeBatch(batchId, 1L);

        // close() drains what was already accepted rather than discarding it.
        repository.close();

        assertEquals(1, repository.recentJobs(10).size());
        assertTrue(repository.findBatch(batchId).isPresent());
    }

    @Test
    @DisplayName("writes after close are dropped and counted, never thrown at the engine")
    void writesAfterCloseAreDropped() {
        repository.close();

        String batchId = "batch-too-late";
        assertDoesNotThrow(() -> {
            repository.saveBatch(batchId, List.of(job(batchId, "k.png")));
            repository.recordOutcome(new JobOutcome.Cancelled(UUID.randomUUID().toString(), 0L));
            repository.completeBatch(batchId, 1L);
        });
        // The barrier returns immediately rather than hanging: a closed repository still releases the
        // latch of every write it discards, which is what stops flush() from blocking for 30 seconds.
        assertTrue(repository.flush(1L, TimeUnit.SECONDS), "flush must not hang on a closed repository");
        assertEquals(0, count("SELECT COUNT(*) FROM batches"), "nothing may reach the database");
        assertTrue(repository.droppedWrites() >= 3,
                "the drops are counted so the UI can say the history is incomplete");
    }

    @Test
    @DisplayName("closing twice is harmless")
    void closeIsIdempotent() {
        repository.close();
        assertDoesNotThrow(repository::close);
    }

    // ------------------------------------------------------------------------
    //  Crash-safe resume: markRunning / findByStatus
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("markRunning moves a job from PENDING to RUNNING, visible via findByStatus")
    void markRunningTransitionsPendingToRunning() {
        String batchId = "batch-mark-running";
        ImageJob first = job(batchId, "p.png");
        ImageJob second = job(batchId, "q.png");
        repository.saveBatch(batchId, List.of(first, second));
        repository.markRunning(first.id());
        assertTrue(repository.flush());

        List<JobRecord> running = repository.findByStatus(JobStatus.RUNNING);
        assertEquals(1, running.size(), () -> "expected only the marked job, got " + running);
        assertEquals(first.id(), running.get(0).jobId());

        List<JobRecord> pending = repository.findByStatus(JobStatus.PENDING);
        assertEquals(1, pending.size());
        assertEquals(second.id(), pending.get(0).jobId());
    }

    @Test
    @DisplayName("recordOutcome after markRunning still reaches the terminal status")
    void markRunningThenOutcomeReachesTerminalStatus() {
        String batchId = "batch-mark-running-then-outcome";
        ImageJob only = job(batchId, "r.png");
        repository.saveBatch(batchId, List.of(only));
        repository.markRunning(only.id());
        repository.recordOutcome(new JobOutcome.Success(only.id(), only.target(), 0L, 1L));
        assertTrue(repository.flush());

        assertEquals(List.of(), repository.findByStatus(JobStatus.RUNNING));
        JobRecord record = repository.recentJobs(10).get(0);
        assertEquals(JobStatus.COMPLETED, record.status());
    }

    @Test
    @DisplayName("markRunning for an unknown or null job id is a harmless no-op")
    void markRunningIgnoresUnknownOrNullJobId() {
        assertDoesNotThrow(() -> {
            repository.markRunning(UUID.randomUUID().toString());
            repository.markRunning(null);
        });
        assertTrue(repository.flush(), "a 0-row UPDATE must not wedge the writer thread");
        assertEquals(List.of(), repository.findByStatus(JobStatus.RUNNING));
    }

    @Test
    @DisplayName("findByStatus for a status nothing is in returns an empty list")
    void findByStatusOfAnUnusedStatusIsEmpty() {
        assertEquals(List.of(), repository.findByStatus(JobStatus.FAILED));
    }

    // ------------------------------------------------------------------------
    //  Schema-level behaviour the adapter depends on
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("deleting a batch cascades to its jobs and their metadata")
    void deletingABatchCascades() throws SQLException {
        // This is really a test of PRAGMA foreign_keys = ON: SQLite defaults it OFF per connection, so
        // without Database applying it the retention purge would silently orphan every jobs row.
        String batchId = "batch-cascade";
        ImageJob only = job(batchId, "l.png");
        repository.saveBatch(batchId, List.of(only));
        repository.recordMetadata(new ImageMetadata(only.id(), 8, 8, "PNG", 64L,
                BufferedImage.TYPE_INT_RGB, false, Map.of()));
        assertTrue(repository.flush());
        assertEquals(1, count("SELECT COUNT(*) FROM jobs"));
        assertEquals(1, count("SELECT COUNT(*) FROM image_metadata"));

        try (Connection connection = database.open();
                PreparedStatement delete =
                        connection.prepareStatement("DELETE FROM batches WHERE batch_id = ?")) {
            delete.setString(1, batchId);
            assertEquals(1, delete.executeUpdate());
        }

        assertEquals(0, count("SELECT COUNT(*) FROM jobs"), "the FK cascade did not fire");
        assertEquals(0, count("SELECT COUNT(*) FROM image_metadata"));
    }

    @Test
    @DisplayName("the seeded presets are readable through the adapter's own connection")
    void seedPresetsAreThere() {
        assertEquals(3, count("SELECT COUNT(*) FROM watermark_presets"));
        assertEquals(3, count("SELECT COUNT(*) FROM pipeline_presets"));
    }

    // ------------------------------------------------------------------------
    //  Retention purge
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("purgeOlderThan deletes a finished batch and cascades to its jobs and metadata")
    void purgeDeletesAFinishedBatchAndCascades() {
        String batchId = "batch-purge-old";
        ImageJob only = job(batchId, "m.png");
        repository.saveBatch(batchId, List.of(only));
        repository.recordMetadata(new ImageMetadata(only.id(), 8, 8, "PNG", 64L,
                BufferedImage.TYPE_INT_RGB, false, Map.of()));
        repository.recordOutcome(new JobOutcome.Success(only.id(), only.target(), 0L, 1L));
        repository.completeBatch(batchId, 1L);
        assertTrue(repository.flush());

        int deleted = repository.purgeOlderThan(Instant.now().plusSeconds(60));

        assertEquals(1, deleted);
        assertEquals(0, count("SELECT COUNT(*) FROM batches WHERE batch_id = '" + batchId + "'"));
        assertEquals(0, count("SELECT COUNT(*) FROM jobs WHERE batch_id = '" + batchId + "'"));
        assertEquals(0, count("SELECT COUNT(*) FROM image_metadata WHERE job_id = '" + only.id() + "'"));
    }

    @Test
    @DisplayName("purgeOlderThan leaves a batch finished after the cutoff untouched")
    void purgeKeepsBatchesFinishedAfterCutoff() {
        String batchId = "batch-purge-recent";
        repository.saveBatch(batchId, List.of(job(batchId, "n.png")));
        repository.completeBatch(batchId, 1L);
        assertTrue(repository.flush());

        int deleted = repository.purgeOlderThan(Instant.now().minus(Duration.ofDays(1)));

        assertEquals(0, deleted);
        assertTrue(repository.findBatch(batchId).isPresent());
    }

    @Test
    @DisplayName("purgeOlderThan never deletes a batch that has not finished, even past the cutoff")
    void purgeKeepsUnfinishedBatches() {
        // finished_at is NULL until completeBatch runs; "NULL < ?" is unknown, not true, in SQL, so an
        // in-flight or abandoned batch survives no matter how old started_at is.
        String batchId = "batch-purge-unfinished";
        repository.saveBatch(batchId, List.of(job(batchId, "o.png")));
        assertTrue(repository.flush());

        int deleted = repository.purgeOlderThan(Instant.now().plusSeconds(60));

        assertEquals(0, deleted);
        assertTrue(repository.findBatch(batchId).isPresent());
    }

    // ------------------------------------------------------------------------
    //  Volume
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("a batch of 500 jobs is recorded completely, in grouped transactions")
    void handlesABatchOfFiveHundred() {
        // MAX_GROUP is 256, so this crosses the grouping boundary twice — the case where a bug in
        // drainTo() or in the statement cache would lose or duplicate rows.
        String batchId = "batch-volume";
        List<ImageJob> jobs = jobs(batchId, 500);
        repository.saveBatch(batchId, jobs);
        for (int i = 0; i < jobs.size(); i++) {
            ImageJob each = jobs.get(i);
            if (i % 10 == 0) {
                repository.recordOutcome(JobOutcome.Failure.from(each.id(),
                        new java.io.IOException("bad file " + i), "decode", 1_000L));
            } else {
                repository.recordOutcome(
                        new JobOutcome.Success(each.id(), each.target(), 1_000_000L, 1_000L));
            }
        }
        repository.completeBatch(batchId, 5_000L);
        assertTrue(repository.flush(60L, TimeUnit.SECONDS));

        BatchSummary summary = repository.findBatch(batchId).orElseThrow();
        assertEquals(500, summary.total());
        assertEquals(50, summary.failed());
        assertEquals(450, summary.succeeded());
        assertEquals(0L, repository.droppedWrites());
        assertEquals(0, count("SELECT COUNT(*) FROM v_batch_counter_drift"));
    }

    // ------------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------------

    private ImageJob job(String batchId, String name) {
        ProcessingOptions options = ProcessingOptions.builder()
                .operations(PIPELINE)
                .outputFormat("jpeg")
                .quality(0.8f)
                .tileThresholdPixels(4096L)
                .batchThresholdJobs(16)
                .build();
        return ImageJob.create(batchId, tempDir.resolve("in").resolve(name),
                tempDir.resolve("out").resolve(name), options);
    }

    private List<ImageJob> jobs(String batchId, int howMany) {
        List<ImageJob> jobs = new ArrayList<>(howMany);
        for (int i = 0; i < howMany; i++) {
            jobs.add(job(batchId, "image-" + i + ".png"));
        }
        return List.copyOf(jobs);
    }

    private int count(String sql) {
        try (Connection connection = database.open();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : -1;
        } catch (SQLException e) {
            throw new AssertionError("query failed: " + sql, e);
        }
    }

    private String string(String sql) {
        try (Connection connection = database.open();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            throw new AssertionError("query failed: " + sql, e);
        }
    }
}
