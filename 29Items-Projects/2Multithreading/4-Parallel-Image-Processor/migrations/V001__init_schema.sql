-- ============================================================================
--  V001__init_schema.sql — batches, jobs, decoded-image metadata
--
--  Target: SQLite 3.37+ (sqlite-jdbc 3.45.x bundles 3.45).
--
--  Conventions used throughout this directory:
--
--   * STRICT tables. Without it SQLite happily stores the string 'oops' in an
--     INTEGER column and the bug surfaces months later in a chart. STRICT turns
--     that into an error at INSERT time. It is the single cheapest correctness
--     win available in SQLite and the reason the minimum version is 3.37.
--
--   * Timestamps are INTEGER epoch milliseconds, not TEXT. They come from
--     System.currentTimeMillis() on the Java side, sort correctly as integers,
--     and need no parsing. V003 defines views that format them for humans.
--
--   * Enums are stored as their Java *name* (TEXT) with a CHECK constraint, not
--     as an ordinal. Reordering the JobStatus enum then cannot silently
--     reinterpret rows already on disk (see JobStatus's javadoc).
--
--   * Durations are milliseconds here, although the domain measures nanoseconds.
--     A nanosecond count is meaningless in a history table nobody queries at
--     that resolution, and ms keeps the numbers legible in ad-hoc SQL.
--
--   * Forward-only. Never edit an applied migration: MigrationRunner stores a
--     checksum per file and refuses to start if one changes. Add V004 instead.
-- ============================================================================

-- ----------------------------------------------------------------------------
--  Migration bookkeeping
--
--  Written by MigrationRunner, inside the same transaction as the migration it
--  describes, so "applied" and "the DDL actually ran" can never disagree.
--  The runner creates this table itself before applying anything (it cannot
--  read its own history from a table that does not exist yet); the redundant
--  definition here keeps the schema readable as plain SQL and is harmless
--  because of IF NOT EXISTS.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS schema_version (
    version      INTEGER PRIMARY KEY,          -- 1 for this file, parsed from V001__
    filename     TEXT    NOT NULL UNIQUE,
    checksum     TEXT    NOT NULL,             -- SHA-256 of the file bytes, hex
    applied_at   INTEGER NOT NULL,             -- epoch millis
    duration_ms  INTEGER NOT NULL DEFAULT 0
) STRICT;

-- ----------------------------------------------------------------------------
--  batches — one row per ImageProcessingEngine.process() call
--
--  The counters are denormalized on purpose. They are written once by
--  completeBatch() and read by every history screen; deriving them with a
--  GROUP BY over jobs on each read would be correct and slower for no benefit,
--  and V003 ships a view that cross-checks them when a discrepancy is suspected.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS batches (
    batch_id          TEXT    NOT NULL PRIMARY KEY,
    started_at        INTEGER NOT NULL,
    finished_at       INTEGER,                 -- NULL while running, or after a crash
    wall_clock_ms     INTEGER NOT NULL DEFAULT 0,

    total             INTEGER NOT NULL DEFAULT 0,
    succeeded         INTEGER NOT NULL DEFAULT 0,
    failed            INTEGER NOT NULL DEFAULT 0,
    cancelled         INTEGER NOT NULL DEFAULT 0,

    -- Snapshot of the ProcessingOptions this batch ran with. Stored per batch
    -- rather than per job because every job in a batch shares one options
    -- record, and stored at all because "why is this output 4000px wide" is the
    -- most common support question and the answer is in here.
    output_format     TEXT    NOT NULL DEFAULT 'png',
    quality           REAL    NOT NULL DEFAULT 0.9,
    pipeline          TEXT    NOT NULL DEFAULT '',   -- 'grayscale>resize>blur'
    tile_threshold_px INTEGER NOT NULL DEFAULT 65536,
    batch_threshold   INTEGER NOT NULL DEFAULT 8,
    parallelism       INTEGER NOT NULL DEFAULT 0,

    CHECK (total     >= 0),
    CHECK (succeeded >= 0),
    CHECK (failed    >= 0),
    CHECK (cancelled >= 0),
    CHECK (quality BETWEEN 0.0 AND 1.0),
    CHECK (finished_at IS NULL OR finished_at >= started_at)
) STRICT;

-- ----------------------------------------------------------------------------
--  jobs — one row per ImageJob, inserted PENDING then updated to a terminal state
--
--  ON DELETE CASCADE gives the retention job (purge batches older than N days)
--  a single DELETE to issue. Note that SQLite enforces foreign keys only when
--  the connection has run `PRAGMA foreign_keys = ON`; Database does that on
--  every connection it opens, because the pragma defaults to OFF for backwards
--  compatibility and a silently-unenforced FK is worse than none.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS jobs (
    job_id           TEXT    NOT NULL PRIMARY KEY,
    batch_id         TEXT    NOT NULL REFERENCES batches(batch_id) ON DELETE CASCADE,

    source_path      TEXT    NOT NULL,
    target_path      TEXT    NOT NULL,
    status           TEXT    NOT NULL DEFAULT 'PENDING',

    duration_ms      INTEGER NOT NULL DEFAULT 0,
    pixels_processed INTEGER NOT NULL DEFAULT 0,

    -- Failure detail, all NULL for a success. exception_type is the *fully
    -- qualified* class name (JobOutcome.Failure.from records getName()): two
    -- frameworks' worth of IllegalStateException are indistinguishable
    -- otherwise. operation_name names the pipeline stage that threw, when the
    -- failure came from a stage rather than from I/O.
    failure_reason   TEXT,
    exception_type   TEXT,
    operation_name   TEXT,

    created_at       INTEGER NOT NULL,
    recorded_at      INTEGER,                  -- when the terminal outcome landed

    CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CHECK (duration_ms >= 0),
    CHECK (pixels_processed >= 0),
    CHECK (source_path <> target_path),        -- in-place processing is unsupported
    -- A FAILED row with no reason is a bug in the adapter, not a legitimate state.
    CHECK (status <> 'FAILED' OR failure_reason IS NOT NULL)
) STRICT;

-- ----------------------------------------------------------------------------
--  image_metadata — facts learned while decoding
--
--  Separate table rather than columns on `jobs` because it is written at a
--  different time (right after decode, by recordMetadata) than the outcome, and
--  because a skipped job never decodes and therefore legitimately has no row.
--  One row per job: the PK is the FK.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS image_metadata (
    job_id        TEXT    NOT NULL PRIMARY KEY REFERENCES jobs(job_id) ON DELETE CASCADE,
    width         INTEGER NOT NULL,
    height        INTEGER NOT NULL,
    format_name   TEXT    NOT NULL,
    source_bytes  INTEGER NOT NULL,
    buffered_type INTEGER NOT NULL,            -- BufferedImage.TYPE_* constant
    has_alpha     INTEGER NOT NULL DEFAULT 0,  -- STRICT has no BOOLEAN
    exif_json     TEXT    NOT NULL DEFAULT '{}',

    CHECK (width  > 0),
    CHECK (height > 0),
    CHECK (source_bytes >= 0),
    CHECK (has_alpha IN (0, 1))
) STRICT;
