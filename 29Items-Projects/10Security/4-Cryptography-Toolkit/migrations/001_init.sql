-- 001_init.sql — initial schema (idempotent, forward-only for v1)
-- Policy: NEVER store plaintexts, keys, or ciphertexts here — metadata only.
-- MUST stay in lockstep with crypto_toolkit/models/* — enforced by
-- backend/tests/test_schema_parity.py (create_all vs this file must match).

CREATE TABLE IF NOT EXISTS users (
    id                    INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    email                 VARCHAR(320) NOT NULL UNIQUE,
    password_hash         TEXT NOT NULL,              -- Argon2id, preset from argon2_service
    is_admin              BOOLEAN NOT NULL DEFAULT 0,
    totp_secret_encrypted TEXT,                       -- Fernet ciphertext (never plaintext)
    totp_last_timestep    INTEGER,                     -- RFC 6238 §5.2 replay guard
    created_at            DATETIME NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS lessons (
    id            INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    slug          VARCHAR(120) NOT NULL UNIQUE,
    title         VARCHAR(200) NOT NULL,
    topic         VARCHAR(60) NOT NULL CHECK (topic IN ('aes','rsa','ecdsa','sha3','argon2','tls')),
    content_md    TEXT NOT NULL DEFAULT '',
    demo_endpoint VARCHAR(200),
    order_index   INTEGER NOT NULL DEFAULT 0,
    created_at    DATETIME NOT NULL DEFAULT (datetime('now')),
    updated_at    DATETIME NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS ix_lessons_topic ON lessons (topic);

-- Audit metadata: which demo ran, when, from where.
-- Deliberately NO payload columns (ARCHITECTURE §2.5).
CREATE TABLE IF NOT EXISTS audit_log (
    id         INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    op         VARCHAR(60) NOT NULL,          -- e.g. 'aes.encrypt', 'argon2.verify'
    params     TEXT NOT NULL DEFAULT '{}',    -- non-secret params only: {"mode":"gcm"}
    ip_hash    VARCHAR(64),                   -- salted hash, not raw IP
    user_id    INTEGER REFERENCES users (id),
    created_at DATETIME NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS ix_audit_op_created_at ON audit_log (op, created_at);
