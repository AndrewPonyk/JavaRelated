#pragma once

#include "engine/core/Json.hpp"
#include "engine/core/Types.hpp"
#include "engine/core/math/Vec.hpp"

#include <string>
#include <string_view>

/// @file Serialization.hpp
/// @brief (De)serialization helpers built on engine::Json.
///
/// All deserialization treats input as untrusted (bounds-checked, no throw across
/// the boundary). `contentHash` backs the AssetDatabase's change detection.

namespace engine::resources {

/// Stable 64-bit FNV-1a content hash, returned as a hex string.
[[nodiscard]] std::string contentHash(std::string_view bytes);

// --- math <-> Json ---
[[nodiscard]] Json       toJson(const math::Vec3& v);
[[nodiscard]] math::Vec3 vec3FromJson(const Json& j, math::Vec3 fallback = {});

} // namespace engine::resources
