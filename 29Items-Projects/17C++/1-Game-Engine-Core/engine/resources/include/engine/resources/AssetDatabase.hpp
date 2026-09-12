#pragma once

#include "engine/core/Result.hpp"
#include "engine/core/Types.hpp"

#include <memory>
#include <optional>
#include <string>
#include <vector>

/// @file AssetDatabase.hpp
/// @brief SQLite-backed catalog of imported assets + the procedural-content cache.
///
/// This is the engine-domain "database" layer. Schema lives in /migrations
/// (001_initial_schema.sql, 002_procgen_cache.sql). The DB lets the engine resolve
/// stable asset GUIDs, detect changed source files by hash, and reuse previously
/// generated terrain tiles instead of re-running ML inference.

namespace engine::resources {

struct AssetRecord {
    std::string guid;        // stable identifier
    std::string sourcePath;  // path of the source file
    std::string contentHash; // hash of the imported content (change detection)
    u32         type = 0;    // AssetType
    i64         importedAt = 0;
};

/// A cached procedurally-generated terrain tile (keyed by seed + biome + model ver).
struct TerrainTileRecord {
    i64         seed = 0;
    i32         tileX = 0;
    i32         tileY = 0;
    std::string biome;
    std::string modelVersion;
    std::string meshBlobPath; // where the baked mesh/heightfield is stored
};

class AssetDatabase {
public:
    AssetDatabase();   // defined in .cpp (pimpl: Impl is incomplete here)
    ~AssetDatabase();

    /// Open (and migrate, if needed) the SQLite database at `path`.
    [[nodiscard]] Result<bool> open(const std::string& path);
    void                        close();

    // --- Asset catalog ---
    [[nodiscard]] Result<bool>               upsertAsset(const AssetRecord& record);
    [[nodiscard]] std::optional<AssetRecord> findByGuid(const std::string& guid);
    [[nodiscard]] std::optional<AssetRecord> findBySourcePath(const std::string& path);

    // --- Procgen cache ---
    [[nodiscard]] std::optional<TerrainTileRecord> findTile(i64 seed, i32 x, i32 y,
                                                            const std::string& modelVersion);
    [[nodiscard]] Result<bool> cacheTile(const TerrainTileRecord& tile);

private:
    struct Impl;                  // wraps sqlite3* (kept out of the header)
    std::unique_ptr<Impl> impl_;
};

} // namespace engine::resources
