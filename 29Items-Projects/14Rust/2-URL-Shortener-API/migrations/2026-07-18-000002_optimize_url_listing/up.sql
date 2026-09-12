DROP INDEX IF EXISTS idx_urls_short_code;
CREATE INDEX idx_urls_created_at_id ON urls(created_at DESC, id DESC);
