#pragma once

#include "engine/core/Result.hpp"
#include "engine/core/Types.hpp"

#include <memory>
#include <string>
#include <vector>

/// @file TerrainModel.hpp
/// @brief Wrapper around an ONNX terrain model run via ONNX Runtime.
///
/// Models are trained offline (tools/ml/train_terrain.py) and exported to ONNX, then
/// loaded here for runtime inference. Inference is pure and deterministic for a given
/// (seed, biome, model version) — the basis for caching and reproducible saves.

namespace engine::procgen {

/// Conditioning inputs for a single tile's heightfield.
struct TerrainInput {
    i64         seed   = 0;
    i32         tileX  = 0;
    i32         tileY  = 0;
    u32         resolution = 256; // NxN heightfield
    std::string biome  = "default";
    f32         heightScale = 1.0f;
};

/// Generated heightfield (row-major, `resolution * resolution` normalized samples).
struct Heightfield {
    u32              resolution = 0;
    std::vector<f32> heights;   // 0..1, scaled by TerrainInput::heightScale downstream
};

class TerrainModel {
public:
    TerrainModel();   // defined in .cpp (pimpl: Impl is incomplete here)
    ~TerrainModel();

    /// Load an exported .onnx model. Records a version string for cache keys.
    [[nodiscard]] Result<bool> load(const std::string& onnxPath, std::string version);
    void                       unload();

    [[nodiscard]] bool        isLoaded() const noexcept;
    [[nodiscard]] const std::string& version() const noexcept { return version_; }

    /// Run inference. Thread-safe to call from worker threads (own session/run opts).
    [[nodiscard]] Result<Heightfield> infer(const TerrainInput& input) const;

private:
    struct Impl;                  // wraps Ort::Session (kept out of the header)
    std::unique_ptr<Impl> impl_;
    std::string           version_;
};

} // namespace engine::procgen
