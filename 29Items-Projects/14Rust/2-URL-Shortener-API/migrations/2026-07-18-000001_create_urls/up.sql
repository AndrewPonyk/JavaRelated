CREATE TABLE urls (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    short_code TEXT NOT NULL UNIQUE,
    long_url TEXT NOT NULL,
    visit_count BIGINT NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'disabled')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL
);
CREATE INDEX idx_urls_short_code ON urls(short_code);
CREATE INDEX idx_urls_status_expires_at ON urls(status, expires_at);
