-- Optional metadata schema for embedders that enable persistent runtime state.

CREATE TABLE IF NOT EXISTS metadata_kv (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS debug_sessions (
    id TEXT PRIMARY KEY,
    target TEXT NOT NULL,
    paused INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS module_cache_entries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    specifier TEXT NOT NULL,
    resolved_path TEXT NOT NULL,
    format TEXT NOT NULL CHECK (format IN ('esm', 'commonjs')),
    bytecode_hash TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(specifier, resolved_path)
);

CREATE TABLE IF NOT EXISTS runtime_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    category TEXT NOT NULL,
    severity TEXT NOT NULL,
    message TEXT NOT NULL,
    metadata_json TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_module_cache_entries_specifier
    ON module_cache_entries(specifier);

CREATE INDEX IF NOT EXISTS idx_runtime_events_category_created_at
    ON runtime_events(category, created_at);

CREATE INDEX IF NOT EXISTS idx_metadata_kv_updated_at
    ON metadata_kv(updated_at);
