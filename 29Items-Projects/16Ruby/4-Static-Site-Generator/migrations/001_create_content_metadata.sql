-- SQLite metadata cache for generated documentation pages.

CREATE TABLE IF NOT EXISTS documentation_versions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  version TEXT NOT NULL UNIQUE,
  is_latest BOOLEAN NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS pages (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  version_id INTEGER NOT NULL,
  title TEXT NOT NULL,
  slug TEXT NOT NULL,
  source_path TEXT NOT NULL,
  output_path TEXT NOT NULL,
  description TEXT,
  canonical_url TEXT,
  tags TEXT NOT NULL DEFAULT '[]',
  checksum TEXT,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (version_id) REFERENCES documentation_versions(id),
  UNIQUE (version_id, slug)
);

CREATE TABLE IF NOT EXISTS page_keywords (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  page_id INTEGER NOT NULL,
  keyword TEXT NOT NULL,
  weight REAL NOT NULL DEFAULT 1.0,
  FOREIGN KEY (page_id) REFERENCES pages(id),
  UNIQUE (page_id, keyword)
);

CREATE INDEX IF NOT EXISTS idx_pages_slug ON pages(slug);
CREATE INDEX IF NOT EXISTS idx_page_keywords_keyword ON page_keywords(keyword);
