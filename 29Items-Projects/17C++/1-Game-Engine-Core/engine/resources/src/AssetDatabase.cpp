#include "engine/resources/AssetDatabase.hpp"

#include "engine/core/Json.hpp"
#include "engine/core/Log.hpp"
#include "engine/platform/Filesystem.hpp"

#include <chrono>
#include <unordered_map>

/// @file AssetDatabase.cpp
/// @brief Working asset/procgen catalog with JSON file persistence.
///
/// This is the dependency-free database backend: an in-memory index persisted
/// atomically to disk. It implements the same semantics as the SQLite schema in
/// /migrations (the documented production backend). The interface is identical, so
/// swapping in real SQLite later is a backend change, not an API change.

namespace engine::resources {
namespace {
i64 nowSeconds() {
    return std::chrono::duration_cast<std::chrono::seconds>(
               std::chrono::system_clock::now().time_since_epoch())
        .count();
}

std::string tileKey(i64 seed, i32 x, i32 y, const std::string& modelVersion) {
    return std::to_string(seed) + ":" + std::to_string(x) + ":" + std::to_string(y) + ":" +
           modelVersion;
}
} // namespace

struct AssetDatabase::Impl {
    std::string                                          path;
    std::unordered_map<std::string, AssetRecord>         assetsByGuid;
    std::unordered_map<std::string, std::string>         guidBySourcePath;
    std::unordered_map<std::string, TerrainTileRecord>   tiles;
    bool                                                 dirty = false;

    void persist() {
        if (path.empty()) {
            return;
        }
        Json root        = Json::object();
        Json assetsArray = Json::array();
        for (const auto& [guid, rec] : assetsByGuid) {
            Json a       = Json::object();
            a["guid"]        = rec.guid;
            a["sourcePath"]  = rec.sourcePath;
            a["contentHash"] = rec.contentHash;
            a["type"]        = rec.type;
            a["importedAt"]  = rec.importedAt;
            assetsArray.push_back(std::move(a));
        }
        root["assets"] = std::move(assetsArray);

        Json tilesArray = Json::array();
        for (const auto& [key, t] : tiles) {
            Json j           = Json::object();
            j["seed"]         = t.seed;
            j["tileX"]        = t.tileX;
            j["tileY"]        = t.tileY;
            j["biome"]        = t.biome;
            j["modelVersion"] = t.modelVersion;
            j["meshBlobPath"] = t.meshBlobPath;
            tilesArray.push_back(std::move(j));
        }
        root["tiles"] = std::move(tilesArray);

        const std::string text = root.dump(2);
        const std::vector<u8> bytes(text.begin(), text.end());
        if (auto r = platform::fs::writeBytesAtomic(path, bytes); !r) {
            log::error("[AssetDB] persist failed: {}", r.error().message);
        } else {
            dirty = false;
        }
    }

    void load() {
        if (!platform::fs::exists(path)) {
            return; // fresh database
        }
        auto text = platform::fs::readText(path);
        if (!text) {
            log::warn("[AssetDB] could not read '{}': {}", path, text.error().message);
            return;
        }
        auto parsed = Json::parse(text.value());
        if (!parsed) {
            log::warn("[AssetDB] corrupt database '{}': {}", path, parsed.error().message);
            return;
        }
        const Json& root = parsed.value();
        for (const Json& a : root.at("assets").arr()) {
            AssetRecord rec;
            rec.guid        = a.at("guid").asString();
            rec.sourcePath  = a.at("sourcePath").asString();
            rec.contentHash = a.at("contentHash").asString();
            rec.type        = static_cast<u32>(a.at("type").asInt());
            rec.importedAt  = a.at("importedAt").asInt();
            guidBySourcePath[rec.sourcePath] = rec.guid;
            assetsByGuid[rec.guid]           = std::move(rec);
        }
        for (const Json& t : root.at("tiles").arr()) {
            TerrainTileRecord rec;
            rec.seed         = t.at("seed").asInt();
            rec.tileX        = static_cast<i32>(t.at("tileX").asInt());
            rec.tileY        = static_cast<i32>(t.at("tileY").asInt());
            rec.biome        = t.at("biome").asString();
            rec.modelVersion = t.at("modelVersion").asString();
            rec.meshBlobPath = t.at("meshBlobPath").asString();
            tiles[tileKey(rec.seed, rec.tileX, rec.tileY, rec.modelVersion)] = std::move(rec);
        }
        log::info("[AssetDB] loaded {} asset(s), {} tile(s) from '{}'", assetsByGuid.size(),
                  tiles.size(), path);
    }
};

AssetDatabase::AssetDatabase() = default;
AssetDatabase::~AssetDatabase() {
    close();
}

Result<bool> AssetDatabase::open(const std::string& path) {
    impl_       = std::make_unique<Impl>();
    impl_->path = path;
    impl_->load();
    return ok(true);
}

void AssetDatabase::close() {
    if (impl_) {
        if (impl_->dirty) {
            impl_->persist();
        }
        impl_.reset();
    }
}

Result<bool> AssetDatabase::upsertAsset(const AssetRecord& record) {
    if (!impl_) {
        return err<bool>(ErrorCode::Unknown, "database not open");
    }
    if (record.guid.empty()) {
        return err<bool>(ErrorCode::InvalidArgument, "asset guid must not be empty");
    }
    AssetRecord rec = record;
    if (rec.importedAt == 0) {
        rec.importedAt = nowSeconds();
    }
    impl_->guidBySourcePath[rec.sourcePath] = rec.guid;
    impl_->assetsByGuid[rec.guid]           = std::move(rec);
    impl_->dirty                            = true;
    impl_->persist(); // write-through so a crash never loses imports
    return ok(true);
}

std::optional<AssetRecord> AssetDatabase::findByGuid(const std::string& guid) {
    if (!impl_) {
        return std::nullopt;
    }
    const auto it = impl_->assetsByGuid.find(guid);
    return it != impl_->assetsByGuid.end() ? std::optional<AssetRecord>(it->second) : std::nullopt;
}

std::optional<AssetRecord> AssetDatabase::findBySourcePath(const std::string& path) {
    if (!impl_) {
        return std::nullopt;
    }
    const auto it = impl_->guidBySourcePath.find(path);
    if (it == impl_->guidBySourcePath.end()) {
        return std::nullopt;
    }
    return findByGuid(it->second);
}

std::optional<TerrainTileRecord> AssetDatabase::findTile(i64 seed, i32 x, i32 y,
                                                         const std::string& modelVersion) {
    if (!impl_) {
        return std::nullopt;
    }
    const auto it = impl_->tiles.find(tileKey(seed, x, y, modelVersion));
    return it != impl_->tiles.end() ? std::optional<TerrainTileRecord>(it->second) : std::nullopt;
}

Result<bool> AssetDatabase::cacheTile(const TerrainTileRecord& tile) {
    if (!impl_) {
        return err<bool>(ErrorCode::Unknown, "database not open");
    }
    impl_->tiles[tileKey(tile.seed, tile.tileX, tile.tileY, tile.modelVersion)] = tile;
    impl_->dirty = true;
    return ok(true); // batched: flushed on close() (tiles are a regenerable cache)
}

} // namespace engine::resources
