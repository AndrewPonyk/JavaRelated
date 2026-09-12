-- migrations/0001_init_model_registry.sql
-- Model registry + detection-run provenance store.
-- Portable SQL (SQLite for dev, PostgreSQL for shared deployments).
--
-- This is the relational backbone for "ML model integration": it tracks which
-- model artifacts exist, their versions/checksums/input specs, the class label
-- sets they emit, and a log of detection runs for audit/observability.

-- ----------------------------------------------------------------------------
-- models: one row per registered, immutable model artifact version.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS models (
    id              INTEGER PRIMARY KEY,
    name            TEXT    NOT NULL,            -- e.g. 'yolov8n'
    version         TEXT    NOT NULL,            -- semver, e.g. '1.3.0'
    backend         TEXT    NOT NULL,            -- 'onnxruntime' | 'opencv_dnn' | 'tensorrt'
    artifact_path   TEXT    NOT NULL,            -- under IMGPROC_MODEL_DIR (allow-listed)
    sha256          TEXT    NOT NULL,            -- verified before load
    input_shape     TEXT    NOT NULL,            -- JSON, e.g. '[1,3,640,640]'
    created_at      TEXT    NOT NULL DEFAULT (CURRENT_TIMESTAMP),
    UNIQUE (name, version)
);

CREATE INDEX IF NOT EXISTS idx_models_name ON models (name);

-- ----------------------------------------------------------------------------
-- labels: class id -> human label for a given model (1 model : N labels).
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS labels (
    id          INTEGER PRIMARY KEY,
    model_id    INTEGER NOT NULL REFERENCES models (id) ON DELETE CASCADE,
    class_id    INTEGER NOT NULL,
    label       TEXT    NOT NULL,                -- e.g. 'person', 'car'
    UNIQUE (model_id, class_id)
);

CREATE INDEX IF NOT EXISTS idx_labels_model ON labels (model_id);

-- ----------------------------------------------------------------------------
-- detection_runs: provenance for each inference invocation (1 model : N runs).
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS detection_runs (
    id                  INTEGER PRIMARY KEY,
    model_id            INTEGER NOT NULL REFERENCES models (id) ON DELETE RESTRICT,
    source_uri          TEXT,                    -- image/video identifier (no PII frames stored)
    device              TEXT    NOT NULL,        -- 'cpu' | 'cuda:0' ...
    num_detections      INTEGER NOT NULL DEFAULT 0,
    latency_ms          REAL,
    created_at          TEXT    NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE INDEX IF NOT EXISTS idx_runs_model    ON detection_runs (model_id);
CREATE INDEX IF NOT EXISTS idx_runs_created  ON detection_runs (created_at);

-- TODO: 0002_add_tracking_sessions.sql — persist track lifetimes per run.
