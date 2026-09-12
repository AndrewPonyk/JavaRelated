-- 001_initial_schema.sql
-- Initial schema for Customer Churn Predictor.
-- Forward-only migration. Apply with psql or a migration runner.

BEGIN;

-- =========================================================================
-- customers: source-of-truth feature rows used for training and scoring.
-- =========================================================================
CREATE TABLE IF NOT EXISTS customers (
    customer_id          TEXT PRIMARY KEY,
    tenure_months        INTEGER       NOT NULL CHECK (tenure_months >= 0),
    monthly_charges      NUMERIC(10,2) NOT NULL CHECK (monthly_charges >= 0),
    total_charges        NUMERIC(12,2),               -- nullable: new customers
    contract_type        TEXT          NOT NULL,
    payment_method       TEXT          NOT NULL,
    num_support_tickets  INTEGER       NOT NULL DEFAULT 0 CHECK (num_support_tickets >= 0),
    is_churned           BOOLEAN,                      -- label; NULL for unscored/new
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_customers_is_churned ON customers (is_churned);
CREATE INDEX IF NOT EXISTS idx_customers_contract   ON customers (contract_type);

-- =========================================================================
-- model_runs: lineage / registry for each training run.
-- =========================================================================
CREATE TABLE IF NOT EXISTS model_runs (
    id               BIGSERIAL PRIMARY KEY,
    algorithm        TEXT        NOT NULL,           -- xgboost | lightgbm | catboost
    metrics          JSONB       NOT NULL,           -- {"holdout_roc_auc": ...}
    params           JSONB       NOT NULL,           -- winning hyperparameters
    feature_columns  JSONB       NOT NULL,           -- exact training column order
    trained_at       TIMESTAMPTZ NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMIT;
