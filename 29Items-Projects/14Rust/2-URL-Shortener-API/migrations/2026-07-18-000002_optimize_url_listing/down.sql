DROP INDEX IF EXISTS idx_urls_created_at_id;
CREATE INDEX idx_urls_short_code ON urls(short_code);
