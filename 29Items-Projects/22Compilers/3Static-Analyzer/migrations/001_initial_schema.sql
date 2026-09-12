CREATE TABLE IF NOT EXISTS analysis_jobs (
  id TEXT PRIMARY KEY,
  project_name TEXT NOT NULL,
  source_path TEXT NOT NULL,
  compile_commands_path TEXT,
  rule_pack TEXT NOT NULL DEFAULT 'default',
  status TEXT NOT NULL CHECK (status IN ('queued', 'running', 'completed', 'failed')),
  ast_facts_json TEXT NOT NULL DEFAULT '{}',
  error_message TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS findings (
  id TEXT PRIMARY KEY,
  analysis_id TEXT NOT NULL REFERENCES analysis_jobs(id) ON DELETE CASCADE,
  rule_id TEXT NOT NULL,
  severity TEXT NOT NULL CHECK (severity IN ('info', 'low', 'medium', 'high', 'critical')),
  message TEXT NOT NULL,
  file_path TEXT NOT NULL,
  line INTEGER NOT NULL,
  column INTEGER NOT NULL,
  evidence_json TEXT NOT NULL DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS rule_definitions (
  id TEXT PRIMARY KEY,
  rule_pack TEXT NOT NULL,
  severity TEXT NOT NULL CHECK (severity IN ('info', 'low', 'medium', 'high', 'critical')),
  description TEXT NOT NULL,
  enabled INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_findings_analysis_id ON findings(analysis_id);
CREATE INDEX IF NOT EXISTS idx_findings_rule_id ON findings(rule_id);
CREATE INDEX IF NOT EXISTS idx_findings_severity ON findings(severity);
CREATE INDEX IF NOT EXISTS idx_rule_definitions_pack ON rule_definitions(rule_pack);
CREATE INDEX IF NOT EXISTS idx_analysis_jobs_status ON analysis_jobs(status);
CREATE INDEX IF NOT EXISTS idx_analysis_jobs_created_at ON analysis_jobs(created_at);
CREATE INDEX IF NOT EXISTS idx_analysis_jobs_rule_pack ON analysis_jobs(rule_pack);
