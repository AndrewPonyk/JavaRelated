-- V0002 — strategy registry and signal audit log.
-- The strategy registry is the source of truth the strategy-engine loads from.

BEGIN;

CREATE TABLE strategy (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name               TEXT        NOT NULL UNIQUE,
    klass              TEXT        NOT NULL,         -- importable Strategy subclass path
    symbols            JSONB       NOT NULL,         -- JSON array of symbols (matches ORM)
    params             JSONB       NOT NULL DEFAULT '{}'::jsonb,
    state              TEXT        NOT NULL DEFAULT 'DRAFT'
                          CHECK (state IN ('DRAFT','BACKTESTING','PAPER','LIVE','HALTED')),
    max_position_qty   BIGINT      NOT NULL CHECK (max_position_qty > 0),
    max_order_notional NUMERIC(18,2) NOT NULL CHECK (max_order_notional > 0),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Now that strategy exists, wire the FK from order.strategy_id.
ALTER TABLE "order"
    ADD CONSTRAINT fk_order_strategy
    FOREIGN KEY (strategy_id) REFERENCES strategy(id);

-- Append-only audit of every signal a strategy emitted (compliance + research).
CREATE TABLE signal (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    strategy_id    UUID        NOT NULL REFERENCES strategy(id),
    symbol         TEXT        NOT NULL REFERENCES instrument(symbol),
    side           TEXT        NOT NULL CHECK (side IN ('BUY','SELL')),
    quantity       BIGINT      NOT NULL CHECK (quantity > 0),
    target_price   NUMERIC(18,4),
    confidence     REAL        NOT NULL DEFAULT 1.0 CHECK (confidence BETWEEN 0 AND 1),
    correlation_id UUID        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_signal_strategy ON signal (strategy_id, created_at);
CREATE INDEX idx_signal_symbol   ON signal (symbol, created_at);

-- Daily PnL snapshot per strategy (projected from fills by the pnl-projector).
CREATE TABLE daily_pnl (
    strategy_id   UUID        NOT NULL REFERENCES strategy(id),
    trade_date    DATE        NOT NULL,
    realized_pnl  NUMERIC(18,4) NOT NULL DEFAULT 0,
    unrealized_pnl NUMERIC(18,4) NOT NULL DEFAULT 0,
    fees          NUMERIC(18,4) NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (strategy_id, trade_date)
);

-- Keep updated_at honest.
CREATE OR REPLACE FUNCTION touch_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_strategy_touch BEFORE UPDATE ON strategy
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER trg_order_touch BEFORE UPDATE ON "order"
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

COMMIT;
