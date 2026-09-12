-- 001_initial_schema.sql
-- Asset catalog for the engine's import pipeline (SQLite).
-- Applied by engine::resources::AssetDatabase on open(). Mirrors AssetRecord.
--
-- Design notes:
--   * `guid` is the stable identity referenced by scenes/prefabs (never the path,
--     so assets can move on disk without breaking references).
--   * `content_hash` enables change detection: re-import only when the source changed.
--   * Dependencies are tracked so changing a texture can invalidate dependent materials.

PRAGMA foreign_keys = ON;
PRAGMA journal_mode = WAL;

CREATE TABLE IF NOT EXISTS schema_version (
    version     INTEGER NOT NULL,
    applied_at  INTEGER NOT NULL DEFAULT (strftime('%s','now'))
);

CREATE TABLE IF NOT EXISTS assets (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    guid          TEXT    NOT NULL UNIQUE,          -- stable identifier (e.g., UUID)
    source_path   TEXT    NOT NULL UNIQUE,          -- path of the source file
    type          INTEGER NOT NULL,                 -- maps to resources::AssetType
    content_hash  TEXT    NOT NULL,                 -- hash of imported content
    size_bytes    INTEGER NOT NULL DEFAULT 0,
    imported_at   INTEGER NOT NULL DEFAULT (strftime('%s','now')),
    CHECK (type >= 0)
);

CREATE INDEX IF NOT EXISTS idx_assets_source ON assets(source_path);
CREATE INDEX IF NOT EXISTS idx_assets_type   ON assets(type);

-- Directed dependency edges between assets (e.g., material -> texture).
CREATE TABLE IF NOT EXISTS asset_dependencies (
    asset_id       INTEGER NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    depends_on_id  INTEGER NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    PRIMARY KEY (asset_id, depends_on_id)
);

INSERT INTO schema_version (version) VALUES (1);
