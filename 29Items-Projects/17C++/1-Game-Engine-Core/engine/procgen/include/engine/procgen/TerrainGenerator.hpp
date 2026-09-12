#pragma once

#include "engine/core/Result.hpp"
#include "engine/core/Types.hpp"
#include "engine/core/math/Vec.hpp"
#include "engine/procgen/TerrainModel.hpp"

#include <functional>
#include <vector>

/// @file TerrainGenerator.hpp
/// @brief Streams ML-generated terrain tiles around the camera.
///
/// On a cache miss it runs TerrainModel inference on a worker thread, builds a mesh +
/// collision from the heightfield, stores the result in the AssetDatabase procgen
/// cache, and notifies the caller. Inference never blocks the main/render thread.

namespace engine::jobs {
class JobSystem;
}
namespace engine::resources {
class AssetDatabase;
}

namespace engine::procgen {

/// A baked terrain tile ready to spawn into the ECS.
struct TerrainTile {
    i32                    tileX = 0;
    i32                    tileY = 0;
    Heightfield            heightfield;
    std::vector<math::Vec3> vertices; // built from the heightfield
    std::vector<u32>        indices;
};

class TerrainGenerator {
public:
    TerrainGenerator(jobs::JobSystem& jobs, const TerrainModel& model,
                     resources::AssetDatabase* cache = nullptr)
        : jobs_(&jobs), model_(&model), cache_(cache) {}

    /// Callback delivered (on a worker thread) when a tile finishes generating.
    using TileReadyFn = std::function<void(TerrainTile&&)>;

    /// Request a tile. If cached, completes quickly; otherwise runs inference async.
    void requestTile(i64 seed, i32 tileX, i32 tileY, const std::string& biome,
                     TileReadyFn onReady);

    /// Convenience: request the ring of tiles around a world position.
    void streamAround(i64 seed, math::Vec3 worldPos, i32 radiusTiles, const std::string& biome,
                      const TileReadyFn& onReady);

private:
    /// Turn a heightfield into a triangle mesh (grid triangulation).
    static void buildMesh(const Heightfield& hf, f32 heightScale, TerrainTile& outTile);

    jobs::JobSystem*           jobs_  = nullptr;
    const TerrainModel*        model_ = nullptr;
    resources::AssetDatabase*  cache_ = nullptr;
};

} // namespace engine::procgen
