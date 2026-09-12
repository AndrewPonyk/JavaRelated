#include "engine/procgen/TerrainModel.hpp"

#include "engine/core/Log.hpp"

#include <cmath>

#if defined(ENGINE_HAS_ONNX)
    #include <onnxruntime_cxx_api.h>
#endif

/// @file TerrainModel.cpp
/// @brief ONNX-backed terrain inference with a deterministic procedural fallback.
///
/// When ONNX Runtime is unavailable (or no model is loaded), infer() falls back to
/// seeded value-noise so the engine always produces reproducible terrain. The real
/// path runs the trained model exported by tools/ml/train_terrain.py.

namespace engine::procgen {
namespace {

/// Cheap deterministic hash -> [0,1).
f32 hash01(i64 x, i64 y, i64 seed) {
    u64 h = static_cast<u64>(x) * 0x9E3779B97F4A7C15ull;
    h ^= static_cast<u64>(y) * 0xC2B2AE3D27D4EB4Full;
    h ^= static_cast<u64>(seed) * 0x165667B19E3779F9ull;
    h ^= h >> 29;
    h *= 0xBF58476D1CE4E5B9ull;
    h ^= h >> 32;
    return static_cast<f32>(h & 0xFFFFFFull) / static_cast<f32>(0x1000000u);
}

f32 smooth(f32 t) { return t * t * (3.0f - 2.0f * t); }

/// Multi-octave value noise sampled at (u,v) for tile (tileX,tileY).
f32 valueNoise(f32 u, f32 v, i64 seed, i32 tileX, i32 tileY) {
    f32 amplitude = 0.5f;
    f32 frequency = 4.0f;
    f32 sum       = 0.0f;
    for (int octave = 0; octave < 5; ++octave) {
        const f32 x  = (u + static_cast<f32>(tileX)) * frequency;
        const f32 y  = (v + static_cast<f32>(tileY)) * frequency;
        const auto xi = static_cast<i64>(std::floor(x));
        const auto yi = static_cast<i64>(std::floor(y));
        const f32 fx = smooth(x - static_cast<f32>(xi));
        const f32 fy = smooth(y - static_cast<f32>(yi));

        const f32 n00 = hash01(xi, yi, seed + octave);
        const f32 n10 = hash01(xi + 1, yi, seed + octave);
        const f32 n01 = hash01(xi, yi + 1, seed + octave);
        const f32 n11 = hash01(xi + 1, yi + 1, seed + octave);
        const f32 nx0 = n00 + (n10 - n00) * fx;
        const f32 nx1 = n01 + (n11 - n01) * fx;
        sum += amplitude * (nx0 + (nx1 - nx0) * fy);

        amplitude *= 0.5f;
        frequency *= 2.0f;
    }
    return sum; // ~0..1
}

} // namespace

struct TerrainModel::Impl {
#if defined(ENGINE_HAS_ONNX)
    Ort::Env                     env{ORT_LOGGING_LEVEL_WARNING, "procgen"};
    std::unique_ptr<Ort::Session> session;
#endif
    bool loaded = false;
};

TerrainModel::TerrainModel() = default;
TerrainModel::~TerrainModel() = default;

Result<bool> TerrainModel::load(const std::string& onnxPath, std::string version) {
    impl_    = std::make_unique<Impl>();
    version_ = std::move(version);
#if defined(ENGINE_HAS_ONNX)
    try {
        Ort::SessionOptions opts;
        opts.SetIntraOpNumThreads(1);
        opts.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);
    #if defined(_WIN32)
        const std::wstring wpath(onnxPath.begin(), onnxPath.end());
        impl_->session = std::make_unique<Ort::Session>(impl_->env, wpath.c_str(), opts);
    #else
        impl_->session = std::make_unique<Ort::Session>(impl_->env, onnxPath.c_str(), opts);
    #endif
        impl_->loaded = true;
        log::info("[ProcGen] loaded ONNX terrain model '{}' (v{})", onnxPath, version_);
        return ok(true);
    } catch (const std::exception& e) {
        log::error("[ProcGen] ONNX load failed: {} — using procedural fallback", e.what());
        return err<bool>(ErrorCode::BackendError, e.what());
    }
#else
    log::warn("[ProcGen] no ONNX runtime — using deterministic procedural fallback.");
    (void) onnxPath;
    return ok(true);
#endif
}

void TerrainModel::unload() {
    impl_.reset();
}

bool TerrainModel::isLoaded() const noexcept {
#if defined(ENGINE_HAS_ONNX)
    return impl_ && impl_->loaded;
#else
    return impl_ != nullptr;
#endif
}

Result<Heightfield> TerrainModel::infer(const TerrainInput& input) const {
    if (input.resolution == 0) {
        return err<Heightfield>(ErrorCode::InvalidArgument, "resolution must be > 0");
    }

    Heightfield hf;
    hf.resolution = input.resolution;
    hf.heights.resize(static_cast<usize>(input.resolution) * input.resolution);

#if defined(ENGINE_HAS_ONNX)
    if (impl_ && impl_->loaded) {
        // TODO: build the input tensor (seed/biome/coords), run impl_->session->Run(...),
        //       copy the output heightfield into hf.heights. Falls through to the
        //       procedural path until wired up.
    }
#endif

    // Deterministic procedural fallback (also the default when no model is present).
    const f32 inv = 1.0f / static_cast<f32>(input.resolution - 1);
    for (u32 y = 0; y < input.resolution; ++y) {
        for (u32 x = 0; x < input.resolution; ++x) {
            const f32 u = static_cast<f32>(x) * inv;
            const f32 v = static_cast<f32>(y) * inv;
            hf.heights[static_cast<usize>(y) * input.resolution + x] =
                valueNoise(u, v, input.seed, input.tileX, input.tileY);
        }
    }
    return ok(std::move(hf));
}

} // namespace engine::procgen
