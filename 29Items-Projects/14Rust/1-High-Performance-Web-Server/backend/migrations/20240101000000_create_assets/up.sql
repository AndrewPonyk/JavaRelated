-- STUB: Example table for storing cached asset metadata
CREATE TABLE assets (
    id SERIAL PRIMARY KEY,
    url_path VARCHAR(255) NOT NULL UNIQUE,
    origin_url TEXT NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    cache_ttl_seconds INT NOT NULL DEFAULT 3600,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_assets_url_path ON assets(url_path);
