-- V001: core schema for the Real-Time Analytics Pipeline.
-- Applied by Flyway (locally via analytics-api startup, in AWS as an explicit CD step).
-- Writers: flink-aggregation-job (metric_aggregates), analytics-api (definitions, alerts).

-- ── Metric registry ─────────────────────────────────────────────────────────
CREATE TABLE metric_definitions (
    metric_key   TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    unit         TEXT,
    description  TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE metric_definitions IS
    'Registry of business metrics; metric_key equals BusinessEvent.eventType';

-- ── Windowed aggregates (durable history; ES holds the hot copy) ────────────
-- NOTE: deliberately NO foreign key to metric_definitions — the stream write path
-- must never fail because a producer emitted a not-yet-registered event type.
-- The relationship is logical (metric_key) and joined at query time.
CREATE TABLE metric_aggregates (
    metric_key      TEXT             NOT NULL,
    window_size     TEXT             NOT NULL CHECK (window_size IN ('1s', '1m')),
    window_start    TIMESTAMPTZ      NOT NULL,
    window_end      TIMESTAMPTZ      NOT NULL,
    dimensions_hash TEXT             NOT NULL DEFAULT '0',
    dimensions      JSONB            NOT NULL DEFAULT '{}'::jsonb,
    event_count     BIGINT           NOT NULL,
    value_sum       DOUBLE PRECISION NOT NULL,
    value_min       DOUBLE PRECISION NOT NULL,
    value_max       DOUBLE PRECISION NOT NULL,
    updated_at      TIMESTAMPTZ      NOT NULL DEFAULT now(),
    -- The PK is the idempotence contract: identical to the Elasticsearch _id
    -- (MetricAggregate.documentId()). Flink upserts ON CONFLICT on this key,
    -- so post-failure replays overwrite instead of duplicating (exactly-once story).
    PRIMARY KEY (metric_key, window_size, window_start, dimensions_hash)
);

CREATE INDEX idx_metric_aggregates_query
    ON metric_aggregates (metric_key, window_size, window_start DESC);

-- TODO(phase-3): convert to native RANGE partitioning on window_start (pg_partman)
-- plus a retention job BEFORE volume grows — repartitioning later is painful.

-- ── Anomaly alerts (workflow: open → acknowledged → resolved) ───────────────
CREATE TABLE anomaly_alerts (
    alert_id      UUID PRIMARY KEY,
    metric_key    TEXT             NOT NULL, -- soft reference to metric_definitions (see note above)
    detector      TEXT             NOT NULL, -- 'ewma-zscore' | 'seasonal-baseline'
    model_version TEXT             NOT NULL DEFAULT '-',
    score         DOUBLE PRECISION NOT NULL, -- signed z-score: + spike, − drop
    threshold     DOUBLE PRECISION NOT NULL,
    observed      DOUBLE PRECISION NOT NULL,
    expected      DOUBLE PRECISION NOT NULL,
    severity      TEXT             NOT NULL CHECK (severity IN ('warning', 'serious', 'critical')),
    window_start  TIMESTAMPTZ      NOT NULL,
    window_end    TIMESTAMPTZ      NOT NULL,
    detected_at   TIMESTAMPTZ      NOT NULL,
    dimensions    JSONB            NOT NULL DEFAULT '{}'::jsonb,
    status        TEXT             NOT NULL DEFAULT 'open'
                                   CHECK (status IN ('open', 'acknowledged', 'resolved')),
    acked_by      TEXT,
    acked_at      TIMESTAMPTZ
);

CREATE INDEX idx_anomaly_alerts_status ON anomaly_alerts (status, detected_at DESC);
CREATE INDEX idx_anomaly_alerts_metric ON anomaly_alerts (metric_key, detected_at DESC);

-- TODO(ops): read-only role for Grafana —
--   CREATE ROLE grafana_ro LOGIN PASSWORD :'grafana_ro_pw';
--   GRANT SELECT ON ALL TABLES IN SCHEMA public TO grafana_ro;
