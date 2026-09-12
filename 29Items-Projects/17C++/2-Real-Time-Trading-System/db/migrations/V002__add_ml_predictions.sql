-- ============================================================================
--  V002__add_ml_predictions.sql
--  Persist advisory ML predictions for post-trade analysis / model monitoring
--  (so we can measure realized signal quality vs. predictions). Off the hot
--  path; written asynchronously like the journal.
-- ============================================================================

CREATE TABLE ml_predictions (
    prediction_id  NUMBER(20)     NOT NULL,
    symbol_id      NUMBER(10)     NOT NULL,
    model_version  VARCHAR2(32)   NOT NULL,
    signal         BINARY_DOUBLE  NOT NULL,        -- expected return, [-1, 1]
    confidence     BINARY_DOUBLE  NOT NULL,        -- [0, 1]
    inference_ns   NUMBER(12),                     -- server-side latency
    predicted_ts   TIMESTAMP(9)   NOT NULL,
    CONSTRAINT pk_ml_predictions PRIMARY KEY (prediction_id),
    CONSTRAINT fk_ml_symbol FOREIGN KEY (symbol_id)
        REFERENCES instruments (symbol_id)
)
PARTITION BY RANGE (predicted_ts) INTERVAL (NUMTODSINTERVAL(1,'DAY'))
( PARTITION p_seed VALUES LESS THAN (TIMESTAMP '2025-01-01 00:00:00') );

CREATE INDEX ix_ml_symbol_ts ON ml_predictions (symbol_id, predicted_ts);

CREATE SEQUENCE seq_prediction_id START WITH 1 INCREMENT BY 1 CACHE 1000;

-- Link table: which prediction (if any) influenced an order, for attribution.
ALTER TABLE orders ADD (prediction_id NUMBER(20));
ALTER TABLE orders ADD CONSTRAINT fk_orders_prediction
    FOREIGN KEY (prediction_id) REFERENCES ml_predictions (prediction_id);
