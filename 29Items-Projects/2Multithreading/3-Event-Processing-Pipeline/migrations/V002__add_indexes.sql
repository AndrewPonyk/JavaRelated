-- ===========================================================================
-- V002 -- indexes, added as a separate migration on purpose.
--
-- Splitting indexes out of V001 is not cosmetic: on a live table an index
-- build takes a lock (or needs CONCURRENTLY, which cannot run inside a
-- transaction), so it is the one part of a schema change most likely to need
-- rescheduling. Keeping it in its own file lets it be applied, timed and
-- rolled back independently of the tables.
--
-- Every index below answers a query this application actually issues or that
-- an operator actually runs. An index nobody queries is pure write cost, and
-- this schema's workload is write-heavy by construction: one bulk insert per
-- run, then read-mostly forever.
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- "The most recent run" -- JdbcAggregateRepository.findLatest().
--
-- DESC matters. Postgres can scan an ASC index backwards, but stating the
-- order the query wants keeps the plan stable and makes the intent readable.
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS ix_pipeline_run_started_at_desc
    ON pipeline_run (started_at DESC);

-- ---------------------------------------------------------------------------
-- "The most recent run for this profile" -- comparing a prod run against the
-- staging soak that preceded it. Composite, env first: the leading column is
-- the equality predicate and the second provides the ordering, so one index
-- serves both without a sort.
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS ix_pipeline_run_env_started_at
    ON pipeline_run (env, started_at DESC);

-- ---------------------------------------------------------------------------
-- "Which runs lost events?" -- the first question after a failure.
--
-- PARTIAL index: lost_events = 0 for every healthy run, so a full index would
-- be almost entirely one repeated value, and the planner would ignore it. The
-- partial version indexes only the rows anyone looks for, which in a healthy
-- database is empty and costs nothing to maintain.
--
-- Portability: partial indexes are Postgres/SQLite. On H2 (used only for
-- integration tests) drop the WHERE clause -- the table there is tiny enough
-- that the difference does not matter.
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS ix_pipeline_run_lost_events
    ON pipeline_run (started_at DESC)
    WHERE lost_events > 0;

-- ---------------------------------------------------------------------------
-- Same reasoning for failures: 'OK' dominates, so index only the exceptions.
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS ix_pipeline_run_failures
    ON pipeline_run (started_at DESC)
    WHERE outcome <> 'OK';

-- ---------------------------------------------------------------------------
-- "How has this sensor behaved across runs?" -- the only cross-run query over
-- the child table.
--
-- The (run_id, sensor_id) direction is already covered by the UNIQUE
-- constraint from V001, which is what the FK join and the per-run report use;
-- duplicating it here would be waste. This index covers the opposite
-- direction, which nothing else provides.
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS ix_sensor_aggregate_sensor_run
    ON sensor_aggregate (sensor_id, run_id DESC);

-- ---------------------------------------------------------------------------
-- Deliberately NOT created, so the omissions are reviewable:
--
--   * sensor_aggregate (run_id)          -- the UNIQUE (run_id, sensor_id)
--                                          index from V001 already serves it
--                                          as a leading-column prefix, which
--                                          is also what makes ON DELETE
--                                          CASCADE fast.
--   * pipeline_run (run_uuid)            -- the UNIQUE constraint is an index.
--   * any index on the numeric counters  -- nobody filters on
--                                          events_produced; a range scan over
--                                          a few thousand run rows is cheaper
--                                          than maintaining the index.
-- ---------------------------------------------------------------------------

-- Fresh statistics immediately after an index build: until the planner has
-- them it may keep using the sequential scan the index was created to avoid.
ANALYZE pipeline_run;
ANALYZE sensor_aggregate;
