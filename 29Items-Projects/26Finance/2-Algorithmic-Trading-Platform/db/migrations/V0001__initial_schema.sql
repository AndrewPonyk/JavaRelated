-- V0001 — initial schema: instruments, accounts, orders, fills.
-- Forward-only migration (Flyway-style). Money uses NUMERIC, never float
-- (TECH-NOTES §3.6). Timestamps are timestamptz (UTC).

BEGIN;

CREATE TABLE instrument (
    symbol        TEXT PRIMARY KEY,
    description   TEXT        NOT NULL,
    asset_class   TEXT        NOT NULL CHECK (asset_class IN ('EQUITY','FUTURE','FX','OPTION','CRYPTO')),
    tick_size     NUMERIC(18,8) NOT NULL CHECK (tick_size > 0),
    lot_size      INTEGER     NOT NULL DEFAULT 1 CHECK (lot_size > 0),
    is_active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE account (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    broker        TEXT        NOT NULL,
    external_ref  TEXT        NOT NULL,            -- broker-side account id
    base_currency TEXT        NOT NULL DEFAULT 'USD',
    is_live       BOOLEAN     NOT NULL DEFAULT FALSE,  -- false = paper
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (broker, external_ref)
);

-- Orders are an audit record; status transitions are append-only events
-- elsewhere, but we keep the latest snapshot here for querying.
CREATE TABLE "order" (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_order_id TEXT        NOT NULL UNIQUE,   -- global idempotency key
    account_id      UUID        REFERENCES account(id),  -- NULL for paper orders
    strategy_id     UUID        NOT NULL,          -- FK added in V0002
    symbol          TEXT        NOT NULL REFERENCES instrument(symbol),
    side            TEXT        NOT NULL CHECK (side IN ('BUY','SELL')),
    order_type      TEXT        NOT NULL CHECK (order_type IN ('MARKET','LIMIT','STOP','STOP_LIMIT')),
    quantity        BIGINT      NOT NULL CHECK (quantity > 0),
    limit_price     NUMERIC(18,4),
    status          TEXT        NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING','APPROVED','REJECTED','WORKING',
                                         'PARTIALLY_FILLED','FILLED','CANCELLED')),
    correlation_id  UUID        NOT NULL,          -- trace back to originating tick
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_order_strategy   ON "order" (strategy_id);
CREATE INDEX idx_order_symbol     ON "order" (symbol);
CREATE INDEX idx_order_status     ON "order" (status);
CREATE INDEX idx_order_created    ON "order" (created_at);

-- Fills are immutable execution facts. High volume → partition by day in prod.
CREATE TABLE fill (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id      UUID        NOT NULL REFERENCES "order"(id),
    symbol        TEXT        NOT NULL REFERENCES instrument(symbol),
    side          TEXT        NOT NULL CHECK (side IN ('BUY','SELL')),
    quantity      BIGINT      NOT NULL CHECK (quantity > 0),
    price         NUMERIC(18,4) NOT NULL CHECK (price > 0),
    commission    NUMERIC(18,4) NOT NULL DEFAULT 0,
    is_partial    BOOLEAN     NOT NULL DEFAULT FALSE,
    executed_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_fill_order  ON fill (order_id);
CREATE INDEX idx_fill_symbol ON fill (symbol, executed_at);

COMMIT;
