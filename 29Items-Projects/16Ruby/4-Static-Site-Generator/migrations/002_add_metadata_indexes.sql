-- Additional indexes for preview API metadata queries.

CREATE INDEX IF NOT EXISTS idx_pages_version_id ON pages(version_id);
CREATE INDEX IF NOT EXISTS idx_pages_updated_at ON pages(updated_at);
CREATE INDEX IF NOT EXISTS idx_documentation_versions_latest ON documentation_versions(is_latest);
