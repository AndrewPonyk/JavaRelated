-- 002_prediction_audit.sql
-- Audit trail of every prediction made through the app (single + batch).
-- Forward-only migration.

BEGIN;

CREATE TABLE IF NOT EXISTS predictions (
    id                 BIGSERIAL PRIMARY KEY,
    customer_id        TEXT          NOT NULL,
    churn_probability  NUMERIC(6,4)  NOT NULL CHECK (churn_probability BETWEEN 0 AND 1),
    risk_band          TEXT          NOT NULL CHECK (risk_band IN ('low','medium','high')),
    model_algorithm    TEXT          NOT NULL,
    top_drivers        JSONB         NOT NULL DEFAULT '[]'::jsonb,  -- SHAP drivers snapshot
    actor              TEXT          NOT NULL DEFAULT 'system',
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Speed up "latest prediction per customer" and time-window reporting.
CREATE INDEX IF NOT EXISTS idx_predictions_customer ON predictions (customer_id);
CREATE INDEX IF NOT EXISTS idx_predictions_created  ON predictions (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_predictions_band     ON predictions (risk_band);

-- Optional FK to customers (kept soft: batch scoring may include unknown ids).
-- ALTER TABLE predictions
--   ADD CONSTRAINT fk_predictions_customer
--   FOREIGN KEY (customer_id) REFERENCES customers (customer_id);

COMMIT;
