-- results_history.sql — SQLite schema for tracking benchmark results over time.
--
-- Purpose: nightly/CI loads each run here so we can chart trends and gate on
-- performance regressions (see .github/workflows/ci.yml bench-regression job).
-- This is the optional "persistence" layer; the live harness only needs CSV/JSON.
--
--   sqlite3 history.db < schema/results_history.sql
--   # then ingest: tools/plot_results.py / a small loader inserts rows

PRAGMA foreign_keys = ON;

-- One row per benchmark *run* (a single invocation of perflib_bench).
CREATE TABLE IF NOT EXISTS run (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    ts               TEXT    NOT NULL,           -- ISO-8601 UTC
    library_version  TEXT    NOT NULL,
    isa              TEXT    NOT NULL CHECK (isa IN ('scalar','avx2','avx512')),
    cpu              TEXT    NOT NULL,            -- CPUID brand string
    compiler         TEXT    NOT NULL,
    assembler        TEXT,
    flags            TEXT    NOT NULL,
    governor         TEXT,
    turbo_disabled   INTEGER,                    -- 0/1
    pinned_core      INTEGER,
    git_commit       TEXT    NOT NULL
);

-- One row per measured (routine, impl, size) within a run.
CREATE TABLE IF NOT EXISTS measurement (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    run_id      INTEGER NOT NULL REFERENCES run(id) ON DELETE CASCADE,
    routine     TEXT    NOT NULL CHECK (routine IN ('saxpy','sdot','sgemm','strlen','memchr')),
    impl        TEXT    NOT NULL CHECK (impl IN ('c','avx2','avx512')),
    size        INTEGER NOT NULL,
    ns_per_call REAL    NOT NULL,
    throughput  REAL    NOT NULL,
    unit        TEXT    NOT NULL CHECK (unit IN ('GB/s','GFLOP/s')),
    speedup     REAL    NOT NULL,
    samples     INTEGER
);

CREATE INDEX IF NOT EXISTS idx_meas_run   ON measurement(run_id);
CREATE INDEX IF NOT EXISTS idx_meas_key   ON measurement(routine, impl, size);
CREATE INDEX IF NOT EXISTS idx_run_commit ON run(git_commit);

-- Latest AVX2 speedup per (routine,size): what a dashboard plots.
CREATE VIEW IF NOT EXISTS v_latest_speedup AS
SELECT m.routine, m.size, m.speedup, m.throughput, m.unit, r.ts, r.git_commit
FROM measurement m
JOIN run r ON r.id = m.run_id
WHERE m.impl = 'avx2'
  AND r.id = (SELECT MAX(id) FROM run);

-- Regression helper: AVX2 speedup of the most recent run vs the previous one,
-- per (routine,size). A negative delta beyond a threshold fails the CI gate.
CREATE VIEW IF NOT EXISTS v_speedup_regression AS
WITH ranked AS (
    SELECT m.routine, m.size, m.speedup, r.id AS run_id,
           RANK() OVER (ORDER BY r.id DESC) AS run_rank
    FROM measurement m JOIN run r ON r.id = m.run_id
    WHERE m.impl = 'avx2'
)
SELECT cur.routine, cur.size,
       prev.speedup AS prev_speedup,
       cur.speedup  AS cur_speedup,
       (cur.speedup - prev.speedup) AS delta
FROM ranked cur
JOIN ranked prev
  ON cur.routine = prev.routine AND cur.size = prev.size
WHERE cur.run_rank = 1 AND prev.run_rank = 2;
