-- 002_procgen_cache.sql
-- Procedural-terrain tile cache. Mirrors resources::TerrainTileRecord.
--
-- Why cache: ML inference per tile is expensive; terrain is deterministic from
-- (seed, tile coords, biome, model_version), so a generated tile can be baked once
-- and reused. The model_version is part of the key so upgrading the model naturally
-- invalidates stale tiles, and a save reloads the exact terrain it shipped with.

CREATE TABLE IF NOT EXISTS terrain_tile_cache (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    seed           INTEGER NOT NULL,
    tile_x         INTEGER NOT NULL,
    tile_y         INTEGER NOT NULL,
    biome          TEXT    NOT NULL,
    model_version  TEXT    NOT NULL,
    mesh_blob_path TEXT    NOT NULL,                 -- baked mesh/heightfield location
    generated_at   INTEGER NOT NULL DEFAULT (strftime('%s','now')),
    UNIQUE (seed, tile_x, tile_y, biome, model_version)
);

-- Primary lookup path: "do we already have this tile for this model version?"
CREATE INDEX IF NOT EXISTS idx_tile_lookup
    ON terrain_tile_cache(seed, tile_x, tile_y, model_version);

-- Catalog of trained terrain models the engine knows about.
CREATE TABLE IF NOT EXISTS terrain_models (
    version     TEXT PRIMARY KEY,                    -- e.g., 'v1'
    onnx_path   TEXT NOT NULL,
    input_res   INTEGER NOT NULL DEFAULT 256,
    notes       TEXT,
    registered_at INTEGER NOT NULL DEFAULT (strftime('%s','now'))
);

INSERT INTO schema_version (version) VALUES (2);
