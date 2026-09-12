-- ===========================================================================
-- V001 -- baseline schema for persisted pipeline runs.
--
-- Dialect: PostgreSQL 14+. Portability notes where a construct is not
-- standard are inline; see migrations/README.md for the H2/SQLite variants.
--
-- Applied by hand or by any migration runner that understands the
-- V<n>__<name>.sql convention (Flyway, Liquibase's SQL format). No runner is
-- committed: the project has zero runtime dependencies and the JDBC
-- repository is a stub, so adding one now would be a dependency in service of
-- code that does not exist yet.
--
-- Idempotent by construction (IF NOT EXISTS throughout) so a partially
-- applied migration can be re-run without a manual cleanup step.
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- pipeline_run -- one row per completed run: the settings it ran with and the
-- counters it produced.
--
-- Why the configuration is denormalised into this table rather than pointing
-- at a config table: a run's numbers are only interpretable together with the
-- settings that produced them, and those settings are immutable history. A
-- foreign key to a mutable config row would let an edit silently rewrite what
-- a past run means.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pipeline_run (
    id                  BIGSERIAL       PRIMARY KEY,

    -- Business key. A caller-supplied UUID rather than the surrogate id, so an
    -- interrupted run that is retried can be recognised as the same logical
    -- run, and so ids are not guessable from the outside.
    run_uuid            UUID            NOT NULL,

    started_at          TIMESTAMPTZ     NOT NULL,
    finished_at         TIMESTAMPTZ     NOT NULL,

    -- Stored explicitly rather than computed from the two timestamps above:
    -- the pipeline measures elapsed time with System.nanoTime() (monotonic),
    -- while the timestamps come from a wall clock that can step backwards over
    -- an NTP correction. Subtracting them can yield a negative duration.
    elapsed_millis      BIGINT          NOT NULL,

    -- 'OK' | 'FAILED' | 'INTERRUPTED'. A CHECK rather than an enum type:
    -- adding a value to a Postgres enum needs DDL and a lock, and this set is
    -- the exit-code contract, which is small and slow-moving but not frozen.
    outcome             VARCHAR(16)     NOT NULL,
    exit_code           SMALLINT        NOT NULL,

    -- Human-readable reason, mirroring PipelineReport.message.
    message             VARCHAR(1024)   NOT NULL DEFAULT '',

    -- --- settings the run used (see PipelineConfig) -------------------------
    env                 VARCHAR(32)     NOT NULL,
    event_count         BIGINT          NOT NULL,
    events_per_second   BIGINT          NOT NULL,
    batch_size          INTEGER         NOT NULL,
    sensor_count        INTEGER         NOT NULL,
    queue_capacity      INTEGER         NOT NULL,
    consumer_threads    INTEGER         NOT NULL,
    filter_threshold    DOUBLE PRECISION NOT NULL,
    aggregation_parallelism INTEGER     NOT NULL,
    random_seed         BIGINT          NOT NULL,

    -- --- counters (MetricsSnapshot) ----------------------------------------
    -- One column per record component of MetricsSnapshot, and nothing else.
    -- Its eventsFiltered() is deliberately absent: that is a derived accessor
    -- for passed + rejected, and a stored copy is a second place for the same
    -- fact to be wrong. lost_events below is the one exception, justified there.
    events_produced     BIGINT          NOT NULL,
    batches_produced    BIGINT          NOT NULL,
    events_passed       BIGINT          NOT NULL,
    events_rejected     BIGINT          NOT NULL,
    events_aggregated   BIGINT          NOT NULL,
    batches_aggregated  BIGINT          NOT NULL,
    errors              BIGINT          NOT NULL,

    -- Denormalised copy of the reconciliation result. Recomputing
    -- produced - (passed + rejected) in every query is both easy to get wrong
    -- and impossible to index; a stored column makes "show me the runs that
    -- lost events" a single indexed scan.
    lost_events         BIGINT          NOT NULL,

    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_pipeline_run_uuid UNIQUE (run_uuid),
    CONSTRAINT ck_pipeline_run_outcome
        CHECK (outcome IN ('OK', 'FAILED', 'INTERRUPTED')),
    CONSTRAINT ck_pipeline_run_exit_code
        CHECK (exit_code IN (0, 1, 2, 130)),
    CONSTRAINT ck_pipeline_run_elapsed
        CHECK (elapsed_millis >= 0),
    CONSTRAINT ck_pipeline_run_window
        CHECK (finished_at >= started_at),
    -- The invariant the whole pipeline is built to preserve, enforced by the
    -- database as well as by the orchestrator. If a bug ever writes a row that
    -- violates it, the insert fails loudly instead of storing a lie.
    CONSTRAINT ck_pipeline_run_reconciles
        CHECK (events_produced = events_passed + events_rejected + lost_events),
    CONSTRAINT ck_pipeline_run_counters_non_negative
        CHECK (events_produced >= 0 AND events_passed >= 0
           AND events_rejected >= 0 AND events_aggregated >= 0
           AND batches_produced >= 0 AND batches_aggregated >= 0
           AND errors >= 0 AND lost_events >= 0)
);

COMMENT ON TABLE  pipeline_run IS
    'One completed pipeline run: the settings it used and the counters it produced.';
COMMENT ON COLUMN pipeline_run.elapsed_millis IS
    'Monotonic elapsed time; do not derive from finished_at - started_at.';
COMMENT ON COLUMN pipeline_run.lost_events IS
    'produced - (passed + rejected). Non-zero is a correctness bug, not a performance note.';

-- ---------------------------------------------------------------------------
-- sensor_aggregate -- one row per sensor per run (AggregateResult).
--
-- min/max/sum are stored and the mean is derived on read, exactly as in the
-- domain record: a stored mean cannot be merged, and merging is the property
-- that makes the fork/join aggregation legal in the first place.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sensor_aggregate (
    id              BIGSERIAL           PRIMARY KEY,

    -- ON DELETE CASCADE: an aggregate row is meaningless without its run, and
    -- deleting old runs is the only retention mechanism this schema needs.
    run_id          BIGINT              NOT NULL
        REFERENCES pipeline_run (id) ON DELETE CASCADE,

    sensor_id       VARCHAR(64)         NOT NULL,
    event_count     BIGINT              NOT NULL,
    min_value       DOUBLE PRECISION    NOT NULL,
    max_value       DOUBLE PRECISION    NOT NULL,
    sum_value       DOUBLE PRECISION    NOT NULL,

    -- One row per sensor per run. Without this the batch insert in
    -- JdbcAggregateRepository.save could double-write on a retry and the
    -- report would silently double every count.
    CONSTRAINT uq_sensor_aggregate_run_sensor UNIQUE (run_id, sensor_id),
    CONSTRAINT ck_sensor_aggregate_count
        CHECK (event_count > 0),
    -- Mirrors the record's invariant. The identity element (count 0, min
    -- +Inf, max -Inf) is never persisted, which is why count > 0 above is
    -- safe and this comparison is unconditional.
    CONSTRAINT ck_sensor_aggregate_bounds
        CHECK (min_value <= max_value)
);

COMMENT ON TABLE  sensor_aggregate IS
    'Per-sensor aggregate for one run: count, min, max, sum. Mean is derived on read.';
COMMENT ON COLUMN sensor_aggregate.sum_value IS
    'Sum, not mean: only sums merge associatively across fork/join partitions.';

-- ---------------------------------------------------------------------------
-- Convenience view. Keeps the average definition in exactly one place, so a
-- reporting query cannot disagree with AggregateResult.average().
-- ---------------------------------------------------------------------------
CREATE OR REPLACE VIEW sensor_aggregate_report AS
SELECT
    r.run_uuid,
    r.env,
    r.started_at,
    a.sensor_id,
    a.event_count,
    a.min_value,
    a.max_value,
    a.sum_value / a.event_count AS avg_value
FROM sensor_aggregate a
JOIN pipeline_run r ON r.id = a.run_id;
