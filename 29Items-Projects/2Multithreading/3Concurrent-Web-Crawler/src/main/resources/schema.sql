-- Concurrent Web Crawler Database Schema
-- SQLite 3.x

-- Enable foreign keys
PRAGMA foreign_keys = ON;

-- Use WAL mode for better concurrency
PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;

-- Crawled pages table
CREATE TABLE IF NOT EXISTS pages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    url TEXT UNIQUE NOT NULL,
    domain TEXT NOT NULL,
    title TEXT,
    content_hash TEXT,
    status_code INTEGER,
    relevance_score REAL DEFAULT 0.0,
    crawled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified TIMESTAMP
);

-- Indexes for pages
CREATE INDEX IF NOT EXISTS idx_pages_url ON pages(url);
CREATE INDEX IF NOT EXISTS idx_pages_domain ON pages(domain);
CREATE INDEX IF NOT EXISTS idx_pages_status_code ON pages(status_code);
CREATE INDEX IF NOT EXISTS idx_pages_content_hash ON pages(content_hash);
CREATE INDEX IF NOT EXISTS idx_pages_domain_status ON pages(domain, status_code);
CREATE INDEX IF NOT EXISTS idx_pages_relevance ON pages(relevance_score DESC);
CREATE INDEX IF NOT EXISTS idx_pages_crawled_at ON pages(crawled_at);

-- Index terms for TF-IDF calculations
CREATE TABLE IF NOT EXISTS index_terms (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    term TEXT UNIQUE NOT NULL,
    document_freq INTEGER DEFAULT 1
);

-- Index for term lookups
CREATE INDEX IF NOT EXISTS idx_terms_term ON index_terms(term);

-- Page-term relationships with TF-IDF scores
CREATE TABLE IF NOT EXISTS page_terms (
    page_id INTEGER NOT NULL,
    term_id INTEGER NOT NULL,
    tf_idf_score REAL NOT NULL,
    term_freq INTEGER NOT NULL,
    PRIMARY KEY (page_id, term_id),
    FOREIGN KEY (page_id) REFERENCES pages(id) ON DELETE CASCADE,
    FOREIGN KEY (term_id) REFERENCES index_terms(id) ON DELETE CASCADE
);

-- Index for efficient joins
CREATE INDEX IF NOT EXISTS idx_page_terms_page ON page_terms(page_id);
CREATE INDEX IF NOT EXISTS idx_page_terms_term ON page_terms(term_id);
CREATE INDEX IF NOT EXISTS idx_page_terms_score ON page_terms(tf_idf_score DESC);

-- Crawl state for resumption capability
CREATE TABLE IF NOT EXISTS crawl_state (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    frontier_snapshot TEXT,
    visited_snapshot TEXT,
    saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status TEXT DEFAULT 'active' CHECK (status IN ('active', 'completed', 'failed'))
);

-- Index for status filtering
CREATE INDEX IF NOT EXISTS idx_crawl_state_status ON crawl_state(status);

-- Robots.txt cache
CREATE TABLE IF NOT EXISTS robots_cache (
    domain TEXT PRIMARY KEY,
    rules TEXT,
    crawl_delay REAL DEFAULT 0.0,
    fetched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP
);

-- Index for expiration checking
CREATE INDEX IF NOT EXISTS idx_robots_expires ON robots_cache(expires_at);

-- Crawl statistics table
CREATE TABLE IF NOT EXISTS crawl_stats (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    crawl_id INTEGER,
    metric_name TEXT NOT NULL,
    metric_value REAL NOT NULL,
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index for metric queries
CREATE INDEX IF NOT EXISTS idx_crawl_stats_crawl ON crawl_stats(crawl_id);
CREATE INDEX IF NOT EXISTS idx_crawl_stats_metric ON crawl_stats(metric_name);

-- View for quick stats
CREATE VIEW IF NOT EXISTS v_crawl_summary AS
SELECT
    COUNT(*) as total_pages,
    COUNT(DISTINCT domain) as unique_domains,
    AVG(relevance_score) as avg_relevance,
    SUM(CASE WHEN status_code = 200 THEN 1 ELSE 0 END) as success_count,
    SUM(CASE WHEN status_code >= 400 THEN 1 ELSE 0 END) as error_count,
    MIN(crawled_at) as first_crawl,
    MAX(crawled_at) as last_crawl
FROM pages;
