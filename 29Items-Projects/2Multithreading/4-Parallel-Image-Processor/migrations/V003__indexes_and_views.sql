-- ============================================================================
--  V003__indexes_and_views.sql — read paths
--
--  Every index here exists for a query the application actually issues. An
--  index nobody uses is not free: it slows every INSERT, and recordOutcome()
--  runs once per image on the hot path of a 10 000-image batch.
--
--  The views exist so that the UI's history screen is one SELECT with no
--  formatting logic in Java, and so that support work can be done in the sqlite3
--  shell without reconstructing the joins from memory.
-- ============================================================================

-- ----------------------------------------------------------------------------
--  Indexes
-- ----------------------------------------------------------------------------

-- recentJobs(limit): ORDER BY recorded_at DESC LIMIT ?. DESC in the index matters
-- — SQLite can walk an ASC index backwards, but a DESC index lets the same plan
-- serve the "newest N" query without a temporary B-tree for the sort.
CREATE INDEX IF NOT EXISTS idx_jobs_recorded_at ON jobs(recorded_at DESC);

-- "show me the jobs of this batch", the UI's drill-down. Covers the FK too,
-- which SQLite does not index automatically: without this, the ON DELETE CASCADE
-- of the retention purge degrades to a full scan of jobs per deleted batch.
CREATE INDEX IF NOT EXISTS idx_jobs_batch ON jobs(batch_id);

-- "which jobs failed", plus the crash-recovery query that re-queues anything
-- left in RUNNING after an unclean shutdown. Partial index: PENDING/COMPLETED
-- rows are the overwhelming majority and are never selected by this predicate,
-- so excluding them keeps the index small enough to stay cached.
CREATE INDEX IF NOT EXISTS idx_jobs_unfinished
    ON jobs(status, batch_id)
    WHERE status IN ('RUNNING', 'FAILED');

-- recentBatches(limit): ORDER BY started_at DESC LIMIT ?.
CREATE INDEX IF NOT EXISTS idx_batches_started_at ON batches(started_at DESC);

-- The retention job's WHERE clause: finished batches older than a cutoff.
CREATE INDEX IF NOT EXISTS idx_batches_finished_at
    ON batches(finished_at)
    WHERE finished_at IS NOT NULL;

-- Deliberately no index on jobs(source_path). Path search is a rare, interactive
-- action where a scan of a few thousand rows is imperceptible, and the column is
-- long, so the index would cost more in write amplification than it returns.

-- ----------------------------------------------------------------------------
--  Views
-- ----------------------------------------------------------------------------

-- One row per job with its batch context and its decoded dimensions, timestamps
-- rendered as ISO-8601 UTC. LEFT JOIN on image_metadata is required, not
-- cosmetic: a job whose target already existed is skipped without decoding and
-- has no metadata row, and an INNER JOIN would hide exactly those rows.
DROP VIEW IF EXISTS v_recent_jobs;
CREATE VIEW v_recent_jobs AS
SELECT
    j.job_id,
    j.batch_id,
    j.status,
    j.source_path,
    j.target_path,
    j.duration_ms,
    j.pixels_processed,
    ROUND(j.pixels_processed / 1000000.0, 2)                        AS megapixels,
    CASE WHEN j.duration_ms > 0
         THEN ROUND(j.pixels_processed / 1000.0 / j.duration_ms, 2)
    END                                                             AS megapixels_per_second,
    j.failure_reason,
    j.exception_type,
    j.operation_name,
    m.width,
    m.height,
    m.format_name,
    m.source_bytes,
    b.output_format,
    b.pipeline,
    -- Both forms of the timestamp. The ISO string is for the sqlite3 shell; the raw millis are what
    -- SqliteJobRepository maps to an Instant, because parsing back a string this view just formatted
    -- would be a round trip through text for no reason. NULL until the outcome lands.
    j.recorded_at,
    strftime('%Y-%m-%dT%H:%M:%SZ', j.recorded_at / 1000, 'unixepoch') AS recorded_at_iso
FROM jobs j
JOIN batches b ON b.batch_id = j.batch_id
LEFT JOIN image_metadata m ON m.job_id = j.job_id
ORDER BY j.recorded_at DESC;

-- Batch history with throughput. total_pixels is summed from jobs rather than
-- stored on batches because, unlike the status counters, nothing on the write
-- path has the total in hand at completeBatch() time.
DROP VIEW IF EXISTS v_batch_summary;
CREATE VIEW v_batch_summary AS
SELECT
    b.batch_id,
    b.total,
    b.succeeded,
    b.failed,
    b.cancelled,
    b.wall_clock_ms,
    b.output_format,
    b.pipeline,
    b.parallelism,
    CASE WHEN b.total > 0
         THEN ROUND(1.0 * b.succeeded / b.total, 4)
    END                                                              AS success_rate,
    COALESCE(SUM(j.pixels_processed), 0)                             AS total_pixels,
    CASE WHEN b.wall_clock_ms > 0
         THEN ROUND(COALESCE(SUM(j.pixels_processed), 0) / 1000.0 / b.wall_clock_ms, 2)
    END                                                              AS megapixels_per_second,
    b.started_at,
    b.finished_at,
    strftime('%Y-%m-%dT%H:%M:%SZ', b.started_at / 1000, 'unixepoch') AS started_at_iso,
    CASE WHEN b.finished_at IS NOT NULL
         THEN strftime('%Y-%m-%dT%H:%M:%SZ', b.finished_at / 1000, 'unixepoch')
    END                                                              AS finished_at_iso
FROM batches b
LEFT JOIN jobs j ON j.batch_id = b.batch_id
GROUP BY b.batch_id
ORDER BY b.started_at DESC;

-- Consistency check, for support rather than for the application. The
-- denormalized counters on `batches` should always agree with the rows in
-- `jobs`; any row this view returns means the write path lost an update, most
-- plausibly a crash between the last recordOutcome() and completeBatch().
DROP VIEW IF EXISTS v_batch_counter_drift;
CREATE VIEW v_batch_counter_drift AS
SELECT
    b.batch_id,
    b.total                                                              AS stored_total,
    COUNT(j.job_id)                                                      AS actual_rows,
    b.succeeded                                                          AS stored_succeeded,
    SUM(CASE WHEN j.status = 'COMPLETED' THEN 1 ELSE 0 END)              AS actual_succeeded,
    b.failed                                                             AS stored_failed,
    SUM(CASE WHEN j.status = 'FAILED'    THEN 1 ELSE 0 END)              AS actual_failed,
    b.cancelled                                                          AS stored_cancelled,
    SUM(CASE WHEN j.status = 'CANCELLED' THEN 1 ELSE 0 END)              AS actual_cancelled
FROM batches b
LEFT JOIN jobs j ON j.batch_id = b.batch_id
GROUP BY b.batch_id
HAVING stored_total     <> actual_rows
    OR stored_succeeded <> actual_succeeded
    OR stored_failed    <> actual_failed
    OR stored_cancelled <> actual_cancelled;
