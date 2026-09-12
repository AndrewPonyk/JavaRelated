-- Flyway V1 — core banking schema (PostgreSQL).
-- Contract for the Exposed-backed PaymentRepository landing in Phase 2
-- (see docs/PROJECT-PLAN.md). Money is BIGINT minor units, always.

CREATE TABLE accounts (
    id             VARCHAR(36)  PRIMARY KEY,
    user_id        VARCHAR(36)  NOT NULL,                    -- Phase 2: auth scoping
    name           VARCHAR(100) NOT NULL,
    iban           VARCHAR(34)  NOT NULL UNIQUE,
    balance_minor  BIGINT       NOT NULL CHECK (balance_minor >= 0),
    currency       CHAR(3)      NOT NULL,
    type           VARCHAR(20)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE transactions (
    id               VARCHAR(36) PRIMARY KEY,
    account_id       VARCHAR(36) NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    payee_name       VARCHAR(100) NOT NULL,
    payee_iban       VARCHAR(34)  NOT NULL,
    amount_minor     BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency         CHAR(3)     NOT NULL,
    direction        VARCHAR(6)  NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    status           VARCHAR(10) NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'DECLINED')),
    reference        VARCHAR(140) NOT NULL DEFAULT '',
    fraud_score      NUMERIC(4, 3) NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transactions_account_created ON transactions (account_id, created_at DESC);
CREATE INDEX idx_transactions_payee           ON transactions (payee_iban);

-- Idempotency: a repeated Idempotency-Key replays the stored response verbatim.
CREATE TABLE idempotency_keys (
    key           VARCHAR(64) PRIMARY KEY,
    account_id    VARCHAR(36) NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    transaction_id VARCHAR(36) NOT NULL REFERENCES transactions (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Fraud review queue fed by FraudDetectionService HIGH-risk verdicts.
CREATE TABLE fraud_alerts (
    id               BIGSERIAL PRIMARY KEY,
    reference_key    VARCHAR(64) NOT NULL,                    -- idempotency key of the attempt
    account_id       VARCHAR(36) NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    score            NUMERIC(4, 3) NOT NULL,
    risk             VARCHAR(6)  NOT NULL CHECK (risk IN ('LOW', 'MEDIUM', 'HIGH')),
    reasons          TEXT        NOT NULL,
    review_status    VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (review_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_fraud_alerts_status ON fraud_alerts (review_status, created_at DESC);
