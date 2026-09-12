-- V0003 — live position projection.
-- Written by the pnl-projector as it folds fills (ARCHITECTURE §2.3.1). The hot
-- read path in production is Redis; this table is the durable projection the
-- api-gateway serves from. Mirrors trading_common.db.models.PositionRow.

BEGIN;

CREATE TABLE position (
    symbol         TEXT PRIMARY KEY,
    quantity       BIGINT      NOT NULL DEFAULT 0,          -- signed: +long / -short
    avg_price      NUMERIC(18,4) NOT NULL DEFAULT 0,
    realized_pnl   NUMERIC(18,4) NOT NULL DEFAULT 0,
    unrealized_pnl NUMERIC(18,4) NOT NULL DEFAULT 0,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_position_touch BEFORE UPDATE ON position
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

COMMIT;
