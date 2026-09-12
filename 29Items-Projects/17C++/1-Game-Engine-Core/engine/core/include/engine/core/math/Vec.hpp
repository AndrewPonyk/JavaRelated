#pragma once

#include "engine/core/Types.hpp"

#include <cmath>

/// @file Vec.hpp
/// @brief Minimal vector types for component data.
///
/// The production build aliases these to GLM (see vcpkg.json) for a full math
/// suite and SIMD; these lightweight structs keep the core self-contained and make
/// component layouts explicit and trivially copyable (data-oriented friendly).

namespace engine::math {

struct Vec2 {
    f32 x = 0.0f, y = 0.0f;
};

struct Vec3 {
    f32 x = 0.0f, y = 0.0f, z = 0.0f;

    friend constexpr Vec3 operator+(Vec3 a, Vec3 b) noexcept { return {a.x + b.x, a.y + b.y, a.z + b.z}; }
    friend constexpr Vec3 operator-(Vec3 a, Vec3 b) noexcept { return {a.x - b.x, a.y - b.y, a.z - b.z}; }
    friend constexpr Vec3 operator*(Vec3 v, f32 s) noexcept { return {v.x * s, v.y * s, v.z * s}; }
};

struct Vec4 {
    f32 x = 0.0f, y = 0.0f, z = 0.0f, w = 0.0f;
};

[[nodiscard]] constexpr f32 dot(Vec3 a, Vec3 b) noexcept {
    return a.x * b.x + a.y * b.y + a.z * b.z;
}

[[nodiscard]] constexpr Vec3 cross(Vec3 a, Vec3 b) noexcept {
    return {a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x};
}

[[nodiscard]] inline f32 length(Vec3 v) noexcept { return std::sqrt(dot(v, v)); }

[[nodiscard]] inline Vec3 normalize(Vec3 v) noexcept {
    const f32 len = length(v);
    return len > 0.0f ? v * (1.0f / len) : v;
}

} // namespace engine::math
