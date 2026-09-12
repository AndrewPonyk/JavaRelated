package com.parallelimage.persistence.repository;

import com.parallelimage.core.model.ImageJob;
import com.parallelimage.core.model.ImageMetadata;
import com.parallelimage.core.model.JobOutcome;
import com.parallelimage.core.model.JobStatus;
import com.parallelimage.core.pipeline.PipelineFormat;
import com.parallelimage.core.port.JobRepository;
import com.parallelimage.persistence.PersistenceException;
import com.parallelimage.persistence.jdbc.Database;
import com.parallelimage.persistence.migration.MigrationRunner;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;

/**
 * SQLite implementation of the {@link JobRepository} port.
 *
 * <h2>One writer thread, and why</h2>
 * SQLite allows exactly one writer at a time. Ten fork/join workers calling {@code recordOutcome}
 * concurrently would therefore collide in C, retry under {@code busy_timeout}, and block image
 * processing on disk I/O — the precise thing the port contract's "cheap per call" clause forbids. So
 * the write methods do not touch the database at all: they build a small closure, drop it on a queue,
 * and return in the tens of nanoseconds it takes to enqueue. A single daemon thread
 * ({@code pip-db-writer}) owns one connection and drains the queue.
 *
 * <p>Draining happens in <em>groups</em>. Committing once per row is roughly a hundred times slower
 * than committing once per hundred rows even with {@code synchronous=NORMAL}, because each commit is a
 * WAL frame write plus a lock cycle. The writer takes one item blocking, drains up to
 * {@value #MAX_GROUP} more that are already waiting, and commits them together. Under load that turns
 * 10 000 commits into ~40; when the queue is quiet it degenerates to one commit per item, which is
 * exactly right for an idle application.
 *
 * <h2>What happens when the group fails</h2>
 * A single bad row — an FK violation because {@code saveBatch} was dropped, a CHECK constraint the
 * adapter got wrong — would roll back the entire group and discard 99 good rows with it. So a failed
 * group is retried item by item, each in its own transaction. The bad one is logged and skipped; the
 * rest land. This is the only place in the codebase that retries, and it retries for that reason.
 *
 * <h2>Bounded queue, and dropping rather than blocking</h2>
 * The queue holds {@value #QUEUE_CAPACITY} items. If it fills — a database on a slow network share
 * behind a 10 000-image batch — writes are <em>dropped</em>, counted, and logged once, never blocked.
 * Blocking would put a fork/join worker to sleep on disk I/O and starve the pool; the whole point of
 * the port's "never throws into the engine" rule is that history is worth less than the batch. See
 * {@link #droppedWrites()}: the UI surfaces a non-zero count as "history is incomplete".
 *
 * <h2>Reads do not go through the queue</h2>
 * Each read opens its own short-lived connection. Under WAL a reader sees a consistent snapshot
 * without blocking the writer or being blocked by it, so a UI refresh during a batch is free. Reads
 * also, unlike writes, throw {@link PersistenceException} — see that class for the reasoning.
 */
public final class SqliteJobRepository implements JobRepository {

    private static final System.Logger LOG =
            System.getLogger(SqliteJobRepository.class.getName());

    /** Maximum extra items folded into one transaction beyond the first. */
    static final int MAX_GROUP = 256;

    /** Queue depth before writes are dropped. ~256 bytes per item, so a few MB at worst. */
    static final int QUEUE_CAPACITY = 20_000;

    /** How long {@link #close()} waits for the queue to drain before giving up on it. */
    private static final long CLOSE_TIMEOUT_SECONDS = 15L;

    /** Identity sentinel telling the writer thread to commit what it has and exit. */
    private static final QueuedWrite SHUTDOWN = new QueuedWrite("shutdown", cache -> { }, null);

    private final Database database;
    private final BlockingQueue<QueuedWrite> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final IntSupplier parallelism;
    private final Thread writerThread;
    private final Connection writeConnection;

    private SqliteJobRepository(Database database, Connection writeConnection,
            IntSupplier parallelism) {
        this.database = database;
        this.writeConnection = writeConnection;
        this.parallelism = parallelism;
        this.writerThread = new Thread(this::drainForever, "pip-db-writer");
        // Daemon so a forgotten close() cannot keep the JVM alive. close() is still the supported
        // path — it drains the queue first, which exiting on a daemon thread would not.
        this.writerThread.setDaemon(true);
        this.writerThread.setUncaughtExceptionHandler((thread, error) -> LOG.log(
                System.Logger.Level.ERROR, "history writer died; history is now read-only", error));
        this.writerThread.start();
    }

    /**
     * Migrates the database and starts the writer.
     *
     * @param parallelism supplies the engine's worker count for the {@code batches.parallelism}
     *     support column. Not part of the port — the engine knows its pool size and the repository
     *     does not — so {@code pip-app} wires it in and everything else passes
     *     {@code () -> 0}, which reads as "not recorded".
     * @throws PersistenceException if the database cannot be opened or migrated. Callers are expected
     *     to catch this and fall back to {@link JobRepository#NO_OP}: an unusable history database is
     *     a reason to lose history, not a reason to refuse to process images.
     */
    public static SqliteJobRepository open(Database database, IntSupplier parallelism) {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(parallelism, "parallelism");
        new MigrationRunner(database).run();
        Connection connection = database.open();
        try {
            return new SqliteJobRepository(database, connection, parallelism);
        } catch (RuntimeException e) {
            Database.closeQuietly(connection);
            throw e;
        }
    }

    /** {@link #open(Database, IntSupplier)} without the parallelism column. */
    public static SqliteJobRepository open(Database database) {
        return open(database, () -> 0);
    }

    // ------------------------------------------------------------------------
    //  Write path — enqueue only, never throws
    // ------------------------------------------------------------------------

    @Override
    public void saveBatch(String batchId, List<ImageJob> jobs) {
        if (batchId == null || jobs == null || jobs.isEmpty()) {
            return;
        }
        // Every job in a batch shares one ProcessingOptions instance (ImageJob carries a reference to
        // it), so the first job is a faithful source for the batch-level snapshot columns. Reading it
        // here rather than adding an options parameter to the port keeps the port describing history
        // rather than configuration.
        List<ImageJob> snapshot = List.copyOf(jobs);
        var options = snapshot.get(0).options();
        String pipeline = PipelineFormat.render(options.operations());
        long now = System.currentTimeMillis();
        int workers = parallelism.getAsInt();

        enqueue("saveBatch " + batchId + " (" + snapshot.size() + " jobs)", cache -> {
            PreparedStatement batch = cache.prepare("""
                    INSERT INTO batches (batch_id, started_at, total, output_format, quality,
                                         pipeline, tile_threshold_px, batch_threshold, parallelism)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(batch_id) DO NOTHING""");
            batch.setString(1, batchId);
            batch.setLong(2, now);
            batch.setInt(3, snapshot.size());
            batch.setString(4, options.outputFormat());
            batch.setDouble(5, options.quality());
            batch.setString(6, pipeline);
            batch.setLong(7, options.tileThresholdPixels());
            batch.setInt(8, options.batchThresholdJobs());
            batch.setInt(9, workers);
            batch.executeUpdate();

            PreparedStatement job = cache.prepare("""
                    INSERT INTO jobs (job_id, batch_id, source_path, target_path, status, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT(job_id) DO NOTHING""");
            // The statement is cached and may carry rows from an attempt that failed mid-executeBatch;
            // without this, the individual retry would re-send them.
            job.clearBatch();
            for (ImageJob queued : snapshot) {
                job.setString(1, queued.id());
                job.setString(2, queued.batchId());
                job.setString(3, queued.source().toString());
                job.setString(4, queued.target().toString());
                job.setString(5, queued.status().name());
                job.setLong(6, now);
                job.addBatch();
            }
            job.executeBatch();
        });
    }

    @Override
    public void recordOutcome(JobOutcome outcome) {
        if (outcome == null) {
            return;
        }
        // Destructure on the caller's thread: the outcome is immutable, so the closure captures only
        // the handful of values it needs and the writer thread never pattern-matches. Keeping the
        // switch here also means a new JobOutcome subtype fails to compile in one obvious place.
        JobStatus status = outcome.toStatus();
        String reason = null;
        String exceptionType = null;
        String operationName = null;
        long pixels = 0L;
        switch (outcome) {
            case JobOutcome.Success success -> pixels = success.pixelsProcessed();
            case JobOutcome.Failure failure -> {
                reason = failure.reason();
                exceptionType = failure.exceptionType();
                operationName = failure.operationName();
            }
            case JobOutcome.Cancelled cancelled -> {
                // Nothing extra to record: a cancelled job has no reason and no pixel count.
            }
        }
        String jobId = outcome.jobId();
        long durationMs = outcome.durationNanos() / 1_000_000L;
        long recordedAt = System.currentTimeMillis();
        String finalReason = reason;
        String finalExceptionType = exceptionType;
        String finalOperationName = operationName;
        long finalPixels = pixels;

        enqueue("recordOutcome " + jobId + " " + status, cache -> {
            PreparedStatement update = cache.prepare("""
                    UPDATE jobs
                       SET status = ?, duration_ms = ?, pixels_processed = ?, failure_reason = ?,
                           exception_type = ?, operation_name = ?, recorded_at = ?
                     WHERE job_id = ?""");
            update.setString(1, status.name());
            update.setLong(2, durationMs);
            update.setLong(3, finalPixels);
            setNullableString(update, 4, finalReason);
            setNullableString(update, 5, finalExceptionType);
            setNullableString(update, 6, finalOperationName);
            update.setLong(7, recordedAt);
            update.setString(8, jobId);
            if (update.executeUpdate() == 0) {
                // Not an error worth failing on: it means saveBatch was dropped or never called, which
                // the drop counter already reports. Debug-level so a --no-history-ish misconfiguration
                // does not fill the log with one line per image.
                LOG.log(System.Logger.Level.DEBUG,
                        () -> "no jobs row for " + jobId + "; outcome not recorded");
            }
        });
    }

    @Override
    public void markRunning(String jobId) {
        if (jobId == null) {
            return;
        }
        enqueue("markRunning " + jobId, cache -> {
            PreparedStatement update = cache.prepare(
                    "UPDATE jobs SET status = ? WHERE job_id = ?");
            update.setString(1, JobStatus.RUNNING.name());
            update.setString(2, jobId);
            update.executeUpdate();
        });
    }

    @Override
    public void recordMetadata(ImageMetadata metadata) {
        if (metadata == null) {
            return;
        }
        String exifJson = toJsonObject(metadata.exif());
        enqueue("recordMetadata " + metadata.jobId(), cache -> {
            PreparedStatement upsert = cache.prepare("""
                    INSERT INTO image_metadata (job_id, width, height, format_name, source_bytes,
                                                buffered_type, has_alpha, exif_json)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(job_id) DO UPDATE SET
                        width         = excluded.width,
                        height        = excluded.height,
                        format_name   = excluded.format_name,
                        source_bytes  = excluded.source_bytes,
                        buffered_type = excluded.buffered_type,
                        has_alpha     = excluded.has_alpha,
                        exif_json     = excluded.exif_json""");
            upsert.setString(1, metadata.jobId());
            upsert.setInt(2, metadata.width());
            upsert.setInt(3, metadata.height());
            upsert.setString(4, metadata.formatName());
            upsert.setLong(5, metadata.sourceBytes());
            upsert.setInt(6, metadata.bufferedType());
            upsert.setInt(7, metadata.hasAlpha() ? 1 : 0);
            upsert.setString(8, exifJson);
            upsert.executeUpdate();
        });
    }

    @Override
    public void completeBatch(String batchId, long wallClockMillis) {
        if (batchId == null) {
            return;
        }
        long finishedAt = System.currentTimeMillis();
        enqueue("completeBatch " + batchId, cache -> {
            // The counters are recomputed from jobs here rather than incremented per outcome. One
            // correlated-subquery UPDATE at batch end (four index scans over idx_jobs_batch) costs
            // less than 10 000 extra UPDATEs on the hot path, and it cannot drift: if it runs, the
            // counters are right by construction. If it never runs — a crash mid-batch — the batch
            // keeps finished_at NULL and v_batch_counter_drift reports it, which is the honest state.
            PreparedStatement update = cache.prepare("""
                    UPDATE batches
                       SET finished_at   = MAX(?, started_at),
                           wall_clock_ms = ?,
                           total     = (SELECT COUNT(*) FROM jobs j WHERE j.batch_id = batches.batch_id),
                           succeeded = (SELECT COUNT(*) FROM jobs j WHERE j.batch_id = batches.batch_id
                                         AND j.status = 'COMPLETED'),
                           failed    = (SELECT COUNT(*) FROM jobs j WHERE j.batch_id = batches.batch_id
                                         AND j.status = 'FAILED'),
                           cancelled = (SELECT COUNT(*) FROM jobs j WHERE j.batch_id = batches.batch_id
                                         AND j.status = 'CANCELLED')
                     WHERE batch_id = ?""");
            update.setLong(1, finishedAt);
            update.setLong(2, Math.max(0L, wallClockMillis));
            update.setString(3, batchId);
            update.executeUpdate();
        });
    }

    // ------------------------------------------------------------------------
    //  Read path — short-lived connections, throws on failure
    // ------------------------------------------------------------------------

    @Override
    public List<JobRecord> recentJobs(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        String sql = """
                SELECT job_id, batch_id, source_path, target_path, status, duration_ms,
                       pixels_processed, failure_reason, recorded_at
                  FROM v_recent_jobs
                 LIMIT ?""";
        try (Connection connection = database.open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                List<JobRecord> records = new ArrayList<>();
                while (rs.next()) {
                    records.add(new JobRecord(
                            rs.getString("job_id"),
                            rs.getString("batch_id"),
                            rs.getString("source_path"),
                            rs.getString("target_path"),
                            readStatus(rs.getString("status")),
                            rs.getLong("duration_ms"),
                            rs.getLong("pixels_processed"),
                            rs.getString("failure_reason"),
                            readInstant(rs, "recorded_at")));
                }
                return List.copyOf(records);
            }
        } catch (SQLException e) {
            throw new PersistenceException("cannot read recent jobs from " + database.file(), e);
        }
    }

    /**
     * Jobs currently in {@code status}, across all batches. Served by {@code idx_jobs_unfinished},
     * which already covers exactly {@code RUNNING}/{@code FAILED} — the two states a caller resuming
     * after a crash cares about.
     */
    @Override
    public List<JobRecord> findByStatus(JobStatus status) {
        Objects.requireNonNull(status, "status");
        String sql = """
                SELECT job_id, batch_id, source_path, target_path, status, duration_ms,
                       pixels_processed, failure_reason, recorded_at
                  FROM jobs
                 WHERE status = ?""";
        try (Connection connection = database.open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            try (ResultSet rs = statement.executeQuery()) {
                List<JobRecord> records = new ArrayList<>();
                while (rs.next()) {
                    records.add(new JobRecord(
                            rs.getString("job_id"),
                            rs.getString("batch_id"),
                            rs.getString("source_path"),
                            rs.getString("target_path"),
                            readStatus(rs.getString("status")),
                            rs.getLong("duration_ms"),
                            rs.getLong("pixels_processed"),
                            rs.getString("failure_reason"),
                            readInstant(rs, "recorded_at")));
                }
                return List.copyOf(records);
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                    "cannot read jobs with status " + status + " from " + database.file(), e);
        }
    }

    @Override
    public List<BatchSummary> recentBatches(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        String sql = batchSummarySelect() + " LIMIT ?";
        try (Connection connection = database.open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                List<BatchSummary> summaries = new ArrayList<>();
                while (rs.next()) {
                    summaries.add(readBatchSummary(rs));
                }
                return List.copyOf(summaries);
            }
        } catch (SQLException e) {
            throw new PersistenceException("cannot read recent batches from " + database.file(), e);
        }
    }

    @Override
    public Optional<BatchSummary> findBatch(String batchId) {
        if (batchId == null || batchId.isBlank()) {
            return Optional.empty();
        }
        String sql = batchSummarySelect() + " WHERE batch_id = ?";
        try (Connection connection = database.open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, batchId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(readBatchSummary(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                    "cannot read batch " + batchId + " from " + database.file(), e);
        }
    }

    /**
     * Deletes every batch finished before {@code cutoff}, a one-shot startup operation rather than a
     * queued write: {@code ServiceRegistry} calls this once, before any batch has run, so there is
     * nothing to batch with and no reason to make the caller wait on the writer thread's queue.
     * {@code ON DELETE CASCADE} on {@code jobs.batch_id} and {@code image_metadata.job_id} removes the
     * dependent rows; {@code idx_batches_finished_at} serves the {@code WHERE} clause.
     */
    @Override
    public int purgeOlderThan(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        String sql = "DELETE FROM batches WHERE finished_at < ?";
        try (Connection connection = database.open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, cutoff.toEpochMilli());
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("cannot purge batches older than " + cutoff
                    + " from " + database.file(), e);
        }
    }

    private static String batchSummarySelect() {
        return """
                SELECT batch_id, total, succeeded, failed, cancelled, wall_clock_ms, started_at
                  FROM v_batch_summary""";
    }

    private static BatchSummary readBatchSummary(ResultSet rs) throws SQLException {
        return new BatchSummary(
                rs.getString("batch_id"),
                rs.getInt("total"),
                rs.getInt("succeeded"),
                rs.getInt("failed"),
                rs.getInt("cancelled"),
                rs.getLong("wall_clock_ms"),
                readInstant(rs, "started_at"));
    }

    /**
     * Maps a stored status name back to the enum, tolerating a value this build does not know.
     *
     * <p>An unknown name means a newer version wrote the row. Failing the whole history read for it
     * would be a poor trade, so it degrades to {@link JobStatus#PENDING} with a warning: the row is
     * visible, mislabelled, and the log says why.
     */
    private static JobStatus readStatus(String name) {
        if (name == null) {
            LOG.log(System.Logger.Level.WARNING, "null job status in history; showing it as PENDING");
            return JobStatus.PENDING;
        }
        try {
            return JobStatus.valueOf(name);
        } catch (IllegalArgumentException e) {
            LOG.log(System.Logger.Level.WARNING,
                    () -> "unknown job status '" + name + "' in history; showing it as PENDING");
            return JobStatus.PENDING;
        }
    }

    /** Reads epoch millis as an {@link Instant}, preserving SQL NULL as {@code null}. */
    private static Instant readInstant(ResultSet rs, String column) throws SQLException {
        long millis = rs.getLong(column);
        return rs.wasNull() ? null : Instant.ofEpochMilli(millis);
    }

    // ------------------------------------------------------------------------
    //  Queue plumbing
    // ------------------------------------------------------------------------

    private void enqueue(String description, WriteOp op) {
        enqueue(new QueuedWrite(description, op, null));
    }

    private void enqueue(QueuedWrite write) {
        if (closed.get()) {
            // Counted, not just logged: a write arriving after close() is history that was silently
            // lost, and droppedWrites() is what lets the UI say so instead of showing a short list as
            // though it were complete. DEBUG rather than WARNING because the ordinary cause is
            // shutdown order, not a fault.
            long total = dropped.incrementAndGet();
            LOG.log(System.Logger.Level.DEBUG, () -> "repository closed; discarding "
                    + write.description() + " (" + total + " dropped so far)");
            runCompletion(write);
            return;
        }
        if (!queue.offer(write)) {
            long total = dropped.incrementAndGet();
            // Log the first drop and then every thousandth: the interesting information is "history
            // started being lost", not each individual loss.
            if (total == 1L || total % 1000L == 0L) {
                LOG.log(System.Logger.Level.WARNING, () -> "history queue full (" + QUEUE_CAPACITY
                        + "); dropped " + total + " writes so far, latest " + write.description()
                        + ". Output files are unaffected.");
            }
            runCompletion(write);
        }
    }

    /**
     * Blocks until every write enqueued before this call has been committed.
     *
     * <p>Exists for tests and for {@code close()}: a test that writes then reads would otherwise race
     * the writer thread, and the usual fix — sleep and hope — is how a suite becomes flaky on a loaded
     * CI machine. The barrier is an ordinary queue item, so it cannot jump ahead of the writes it is
     * waiting for.
     *
     * @return {@code false} if the wait timed out or was interrupted
     */
    public boolean flush(long timeout, TimeUnit unit) {
        if (closed.get()) {
            // Nothing can still be in flight that close() did not already wait for, and going through
            // the queue would only inflate the drop counter with a barrier that is not lost history.
            // A close() racing this check can still count one barrier; that is a counter off by one
            // during shutdown, which is cheaper than the bookkeeping to prevent it.
            return true;
        }
        CountDownLatch done = new CountDownLatch(1);
        enqueue(new QueuedWrite("flush barrier", cache -> { }, done));
        try {
            return done.await(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** {@link #flush(long, TimeUnit)} with a timeout long enough that only a hang exceeds it. */
    public boolean flush() {
        return flush(30L, TimeUnit.SECONDS);
    }

    /** Number of writes discarded because the queue was full or the repository was closed. */
    public long droppedWrites() {
        return dropped.get();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        // Bypass offer()'s closed check by going straight at the queue: the sentinel must get in even
        // though the repository is now closed, or the writer thread never learns to stop.
        if (!queue.offer(SHUTDOWN)) {
            LOG.log(System.Logger.Level.WARNING,
                    "history queue full at shutdown; interrupting the writer");
            writerThread.interrupt();
        }
        try {
            writerThread.join(TimeUnit.SECONDS.toMillis(CLOSE_TIMEOUT_SECONDS));
            if (writerThread.isAlive()) {
                LOG.log(System.Logger.Level.WARNING, () -> "history writer did not finish within "
                        + CLOSE_TIMEOUT_SECONDS + "s; " + queue.size() + " writes abandoned");
                writerThread.interrupt();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // The writer closes its own connection on exit; this covers the case where it never got there.
        Database.closeQuietly(writeConnection);
    }

    // ------------------------------------------------------------------------
    //  The writer thread
    // ------------------------------------------------------------------------

    // PMD.UseTryWithResources: the cache is in a try-with-resources; writeConnection cannot be,
    // because it is a field that outlives every method and is opened once when the writer thread
    // starts. Its close belongs in this finally block, which runs exactly when the thread exits.
    @SuppressWarnings("PMD.UseTryWithResources")
    private void drainForever() {
        try (StatementCache cache = new StatementCache(writeConnection)) {
            List<QueuedWrite> group = new ArrayList<>(MAX_GROUP + 1);
            while (true) {
                QueuedWrite first = queue.take();
                if (first == SHUTDOWN) {
                    // Drain whatever arrived before the sentinel — those callers were told their write
                    // was accepted, and close() promised to wait for it.
                    group.clear();
                    queue.drainTo(group);
                    group.removeIf(write -> write == SHUTDOWN);
                    if (!group.isEmpty()) {
                        commitGroup(cache, group);
                    }
                    return;
                }
                group.clear();
                group.add(first);
                queue.drainTo(group, MAX_GROUP);
                boolean shutdownSeen = group.removeIf(write -> write == SHUTDOWN);
                commitGroup(cache, group);
                if (shutdownSeen) {
                    return;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.log(System.Logger.Level.DEBUG, "history writer interrupted; exiting");
        } finally {
            // The cache is closed by try-with-resources above, before this runs — which is the
            // order that matters: a PreparedStatement must not outlive the connection it was
            // prepared on.
            Database.closeQuietly(writeConnection);
        }
    }

    /** Commits a group in one transaction, falling back to one-at-a-time if it fails. */
    // PMD.CloseResource: the connection is borrowed from the cache, not acquired here. It belongs to
    // the writer thread for the life of the process and is closed by drainForever's finally block;
    // closing it here would end history writing after the first group.
    @SuppressWarnings("PMD.CloseResource")
    private void commitGroup(StatementCache cache, List<QueuedWrite> group) {
        if (group.isEmpty()) {
            return;
        }
        Connection connection = cache.connection();
        try {
            connection.setAutoCommit(false);
            for (QueuedWrite write : group) {
                write.op().execute(cache);
            }
            connection.commit();
        } catch (SQLException e) {
            Database.rollbackQuietly(connection);
            if (group.size() == 1) {
                LOG.log(System.Logger.Level.WARNING, () -> "history write failed ("
                        + group.get(0).description() + "); output files are unaffected", e);
            } else {
                LOG.log(System.Logger.Level.DEBUG,
                        () -> "group of " + group.size() + " failed; retrying individually", e);
                retryIndividually(cache, group);
            }
        } finally {
            restoreAutoCommit(connection);
            group.forEach(SqliteJobRepository::runCompletion);
        }
    }

    /** Same borrowed connection as {@link #commitGroup}; see the note there. */
    @SuppressWarnings("PMD.CloseResource")
    private void retryIndividually(StatementCache cache, List<QueuedWrite> group) {
        Connection connection = cache.connection();
        for (QueuedWrite write : group) {
            try {
                connection.setAutoCommit(false);
                write.op().execute(cache);
                connection.commit();
            } catch (SQLException e) {
                Database.rollbackQuietly(connection);
                LOG.log(System.Logger.Level.WARNING, () -> "history write failed ("
                        + write.description() + "); output files are unaffected", e);
            } finally {
                restoreAutoCommit(connection);
            }
        }
    }

    private static void restoreAutoCommit(Connection connection) {
        try {
            if (!connection.isClosed() && !connection.getAutoCommit()) {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOG.log(System.Logger.Level.DEBUG, "cannot restore auto-commit", e);
        }
    }

    private static void runCompletion(QueuedWrite write) {
        if (write.done() != null) {
            write.done().countDown();
        }
    }

    // ------------------------------------------------------------------------
    //  Small helpers
    // ------------------------------------------------------------------------

    private static void setNullableString(PreparedStatement statement, int index, String value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    /**
     * Renders a flat string map as a JSON object.
     *
     * <p>Hand-rolled rather than pulled in as a dependency because this is the only JSON this module
     * writes, the input is a {@code Map<String, String>} with no nesting, and {@code exif_json} is a
     * support-and-diagnostics column. A dependency for eight lines of escaping would be a poor trade
     * in a module whose whole reason for existing is to keep {@code pip-core} dependency-free.
     */
    static String toJsonObject(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return "{}";
        }
        StringBuilder json = new StringBuilder(32 * values.size()).append('{');
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            appendJsonString(json, entry.getKey());
            json.append(':');
            if (entry.getValue() == null) {
                json.append("null");
            } else {
                appendJsonString(json, entry.getValue());
            }
        }
        return json.append('}').toString();
    }

    private static void appendJsonString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    @Override
    public String toString() {
        return "SqliteJobRepository[" + database.file() + ", queued=" + queue.size()
                + ", dropped=" + dropped.get() + "]";
    }

    // ------------------------------------------------------------------------
    //  Types
    // ------------------------------------------------------------------------

    /** A unit of work for the writer thread. Runs inside a transaction it does not manage. */
    @FunctionalInterface
    private interface WriteOp {
        void execute(StatementCache cache) throws SQLException;
    }

    /**
     * @param description what this write is, for log lines — built at enqueue time because the writer
     *     thread has no other way to say which write failed
     * @param done latch released once the write has been committed or abandoned, or {@code null}
     */
    private record QueuedWrite(String description, WriteOp op, CountDownLatch done) {}

    /**
     * Per-connection {@link PreparedStatement} cache.
     *
     * <p>The alternative is preparing every statement on every write, and at one image per statement
     * over a 10 000-image batch that is 10 000 parses of the same SQL. Safe here precisely because the
     * cache is confined to the writer thread and its one connection; a shared cache would be a
     * concurrency bug, since a {@code PreparedStatement} is not thread-safe.
     */
    private static final class StatementCache implements AutoCloseable {

        private final Connection connection;
        private final Map<String, PreparedStatement> statements = new HashMap<>();

        StatementCache(Connection connection) {
            this.connection = connection;
        }

        Connection connection() {
            return connection;
        }

        PreparedStatement prepare(String sql) throws SQLException {
            PreparedStatement cached = statements.get(sql);
            if (cached != null) {
                cached.clearParameters();
                return cached;
            }
            PreparedStatement prepared = connection.prepareStatement(sql);
            statements.put(sql, prepared);
            return prepared;
        }

        // PMD.CloseResource: every statement *is* closed, on the next line. PMD recognises a close
        // only in a finally block or a try-with-resources header, and neither fits a loop over a
        // map's values: the try/catch is what stops one uncooperative statement from abandoning the
        // rest, which is the whole reason this method is not a one-line forEach.
        @SuppressWarnings("PMD.CloseResource")
        @Override
        public void close() {
            for (PreparedStatement statement : statements.values()) {
                try {
                    statement.close();
                } catch (SQLException e) {
                    LOG.log(System.Logger.Level.DEBUG, "cannot close cached statement", e);
                }
            }
            statements.clear();
        }
    }
}
