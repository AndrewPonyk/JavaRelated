CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS api_keys (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  key_hash TEXT NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_used_at TIMESTAMPTZ,
  revoked_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS urls (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  short_code TEXT NOT NULL UNIQUE,
  original_url TEXT NOT NULL,
  title TEXT NOT NULL DEFAULT '',
  owner_key_id UUID REFERENCES api_keys(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ,
  deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_urls_short_code_active
  ON urls (short_code)
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_urls_created_at
  ON urls (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_urls_owner_key_id
  ON urls (owner_key_id)
  WHERE owner_key_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_urls_expires_at
  ON urls (expires_at)
  WHERE expires_at IS NOT NULL;
