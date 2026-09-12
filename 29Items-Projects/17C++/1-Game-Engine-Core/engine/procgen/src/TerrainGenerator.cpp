#include "engine/procgen/TerrainGenerator.hpp"

#include "engine/core/Log.hpp"
#include "engine/core/jobs/JobSystem.hpp"
#include "engine/resources/AssetDatabase.hpp"

#include <cmath>
#include <utility>

/// @file TerrainGenerator.cpp
/// @brief Async terrain tile generation: cache lookup -> ML inference -> mesh bake.

namespace engine::procgen {

void TerrainGenerator::buildMesh(const Heightfield& hf, f32 heightScale, TerrainTile& outTile) {
    const u32 n = hf.resolution;
    if (n == 0) {
        return;
    }
    outTile.vertices.reserve(static_cast<usize>(n) * n);
    const f32 step = 1.0f / static_cast<f32>(n - 1);
    for (u32 y = 0; y < n; ++y) {
        for (u32 x = 0; x < n; ++x) {
            const f32 h = hf.heights[static_cast<usize>(y) * n + x] * heightScale;
            outTile.vertices.push_back(math::Vec3{static_cast<f32>(x) * step, h,
                                                  static_cast<f32>(y) * step});
        }
    }
    // Two triangles per grid cell.
    outTile.indices.reserve(static_cast<usize>(n - 1) * (n - 1) * 6);
    for (u32 y = 0; y < n - 1; ++y) {
        for (u32 x = 0; x < n - 1; ++x) {
            const u32 i0 = y * n + x;
            const u32 i1 = i0 + 1;
            const u32 i2 = i0 + n;
            const u32 i3 = i2 + 1;
            outTile.indices.insert(outTile.indices.end(), {i0, i2, i1, i1, i2, i3});
        }
    }
}

void TerrainGenerator::requestTile(i64 seed, i32 tileX, i32 tileY, const std::string& biome,
                                   TileReadyFn onReady) {
    const std::string modelVersion = model_->version();
    jobs_->dispatch([this, seed, tileX, tileY, biome, modelVersion, cb = std::move(onReady)] {
        // Cache lookup: terrain is deterministic from (seed, coords, model version), so a
        // hit means we already recorded this tile's provenance/blob.
        if (cache_ != nullptr) {
            if (auto cached = cache_->findTile(seed, tileX, tileY, modelVersion)) {
                log::debug("[ProcGen] cache hit for tile ({},{})", tileX, tileY);
            }
        }

        TerrainInput in;
        in.seed  = seed;
        in.tileX = tileX;
        in.tileY = tileY;
        in.biome = biome;

        auto hf = model_->infer(in);
        if (!hf) {
            log::warn("[ProcGen] tile ({},{}) inference failed: {}", tileX, tileY,
                      hf.error().message);
            return;
        }

        TerrainTile tile;
        tile.tileX       = tileX;
        tile.tileY       = tileY;
        tile.heightfield = std::move(hf.value());
        buildMesh(tile.heightfield, in.heightScale, tile);

        // Record provenance in the cache (regenerable, so batched/flushed on close()).
        if (cache_ != nullptr) {
            resources::TerrainTileRecord rec;
            rec.seed         = seed;
            rec.tileX        = tileX;
            rec.tileY        = tileY;
            rec.biome        = biome;
            rec.modelVersion = modelVersion;
            rec.meshBlobPath = "procgen/tile_" + std::to_string(tileX) + "_" +
                               std::to_string(tileY) + ".mesh";
            (void) cache_->cacheTile(rec);
        }

        if (cb) {
            cb(std::move(tile));
        }
    });
}

void TerrainGenerator::streamAround(i64 seed, math::Vec3 worldPos, i32 radiusTiles,
                                    const std::string& biome, const TileReadyFn& onReady) {
    // World-to-tile mapping: 1 world unit == 1 tile.
    const auto cx = static_cast<i32>(std::floor(worldPos.x));
    const auto cz = static_cast<i32>(std::floor(worldPos.z));
    for (i32 dy = -radiusTiles; dy <= radiusTiles; ++dy) {
        for (i32 dx = -radiusTiles; dx <= radiusTiles; ++dx) {
            requestTile(seed, cx + dx, cz + dy, biome, onReady);
        }
    }
}

} // namespace engine::procgen
