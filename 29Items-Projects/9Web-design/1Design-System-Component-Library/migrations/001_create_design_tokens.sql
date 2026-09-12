CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS design_token_sets (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(120) NOT NULL UNIQUE,
  description TEXT,
  source VARCHAR(80) NOT NULL DEFAULT 'local',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS design_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  token_set_id UUID NOT NULL REFERENCES design_token_sets(id) ON DELETE CASCADE,
  name VARCHAR(160) NOT NULL,
  category VARCHAR(40) NOT NULL CHECK (
    category IN ('color', 'typography', 'spacing', 'radius', 'shadow', 'motion', 'zIndex')
  ),
  value TEXT NOT NULL,
  description TEXT,
  status VARCHAR(32) NOT NULL DEFAULT 'draft' CHECK (
    status IN ('draft', 'approved', 'rejected', 'deprecated')
  ),
  deprecated BOOLEAN NOT NULL DEFAULT FALSE,
  figma_node_id VARCHAR(160),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (token_set_id, name)
);

CREATE TABLE IF NOT EXISTS design_token_versions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  token_id UUID NOT NULL REFERENCES design_tokens(id) ON DELETE CASCADE,
  version INTEGER NOT NULL,
  value TEXT NOT NULL,
  status VARCHAR(32) NOT NULL CHECK (
    status IN ('draft', 'approved', 'rejected', 'deprecated')
  ),
  change_note TEXT,
  created_by VARCHAR(160),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (token_id, version)
);

CREATE TABLE IF NOT EXISTS design_token_audit_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  token_id UUID REFERENCES design_tokens(id) ON DELETE SET NULL,
  token_set_id UUID REFERENCES design_token_sets(id) ON DELETE SET NULL,
  event_type VARCHAR(80) NOT NULL,
  event_payload JSONB NOT NULL DEFAULT '{}',
  created_by VARCHAR(160),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_design_tokens_category ON design_tokens(category);
CREATE INDEX IF NOT EXISTS idx_design_tokens_status ON design_tokens(status);
CREATE INDEX IF NOT EXISTS idx_design_tokens_token_set_id ON design_tokens(token_set_id);
CREATE INDEX IF NOT EXISTS idx_design_tokens_name_lower ON design_tokens(LOWER(name));
CREATE INDEX IF NOT EXISTS idx_design_token_versions_token_id ON design_token_versions(token_id);
CREATE INDEX IF NOT EXISTS idx_design_token_audit_events_token_id ON design_token_audit_events(token_id);
CREATE INDEX IF NOT EXISTS idx_design_token_audit_events_token_set_id ON design_token_audit_events(token_set_id);
