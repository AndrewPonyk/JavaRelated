-- ============================================================================
--  V001__initial_schema.sql   (Oracle, Flyway-style forward-only migration)
--  Core system-of-record tables: instruments, orders, executions, positions.
--  Designed for high-throughput append (journaling) + EOD reporting queries.
-- ============================================================================

-- Instruments / reference data ---------------------------------------------
CREATE TABLE instruments (
    symbol_id    NUMBER(10)      NOT NULL,
    ticker       VARCHAR2(16)    NOT NULL,
    venue        VARCHAR2(16)    NOT NULL,
    tick_size    NUMBER(18,9)    NOT NULL,
    lot_size     NUMBER(18)      NOT NULL,
    active       CHAR(1) DEFAULT 'Y' NOT NULL,
    CONSTRAINT pk_instruments PRIMARY KEY (symbol_id),
    CONSTRAINT uq_instruments_ticker UNIQUE (ticker, venue),
    CONSTRAINT ck_instruments_active CHECK (active IN ('Y','N'))
);

-- Orders: one row per order, updated through its lifecycle -------------------
CREATE TABLE orders (
    order_id     NUMBER(20)      NOT NULL,
    strategy_id  NUMBER(10)      NOT NULL,
    symbol_id    NUMBER(10)      NOT NULL,
    side         CHAR(1)         NOT NULL,           -- B / S
    ord_type     VARCHAR2(4)     NOT NULL,           -- MKT/LMT/STP/STL
    tif          VARCHAR2(4)     NOT NULL,           -- DAY/IOC/FOK/GTC
    price        NUMBER(18,9),
    quantity     NUMBER(18)      NOT NULL,
    filled_qty   NUMBER(18) DEFAULT 0 NOT NULL,
    state        VARCHAR2(16)    NOT NULL,
    created_ts   TIMESTAMP(9)    NOT NULL,
    updated_ts   TIMESTAMP(9)    NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (order_id),
    CONSTRAINT fk_orders_symbol FOREIGN KEY (symbol_id)
        REFERENCES instruments (symbol_id),
    CONSTRAINT ck_orders_side CHECK (side IN ('B','S'))
)
PARTITION BY RANGE (created_ts) INTERVAL (NUMTODSINTERVAL(1,'DAY'))
( PARTITION p_seed VALUES LESS THAN (TIMESTAMP '2025-01-01 00:00:00') );

CREATE INDEX ix_orders_symbol_state ON orders (symbol_id, state);
CREATE INDEX ix_orders_strategy     ON orders (strategy_id, created_ts);

-- Executions: immutable fill records (append-only audit trail) ---------------
CREATE TABLE executions (
    exec_id      NUMBER(20)      NOT NULL,
    order_id     NUMBER(20)      NOT NULL,
    last_qty     NUMBER(18)      NOT NULL,
    last_px      NUMBER(18,9)    NOT NULL,
    cum_qty      NUMBER(18)      NOT NULL,
    exec_ts      TIMESTAMP(9)    NOT NULL,
    CONSTRAINT pk_executions PRIMARY KEY (exec_id),
    CONSTRAINT fk_exec_order FOREIGN KEY (order_id) REFERENCES orders (order_id)
)
PARTITION BY RANGE (exec_ts) INTERVAL (NUMTODSINTERVAL(1,'DAY'))
( PARTITION p_seed VALUES LESS THAN (TIMESTAMP '2025-01-01 00:00:00') );

CREATE INDEX ix_exec_order ON executions (order_id);

-- Positions: net position snapshot per strategy/symbol -----------------------
CREATE TABLE positions (
    strategy_id  NUMBER(10)      NOT NULL,
    symbol_id    NUMBER(10)      NOT NULL,
    net_qty      NUMBER(18)      NOT NULL,
    avg_px       NUMBER(18,9)    NOT NULL,
    realized_pnl NUMBER(20,4) DEFAULT 0 NOT NULL,
    as_of_ts     TIMESTAMP(9)    NOT NULL,
    CONSTRAINT pk_positions PRIMARY KEY (strategy_id, symbol_id),
    CONSTRAINT fk_pos_symbol FOREIGN KEY (symbol_id)
        REFERENCES instruments (symbol_id)
);

-- Sequence for synthetic ids (orders/exec ids are engine-assigned, but reports
-- and ad-hoc inserts may use these).
CREATE SEQUENCE seq_exec_id START WITH 1 INCREMENT BY 1 CACHE 1000;
