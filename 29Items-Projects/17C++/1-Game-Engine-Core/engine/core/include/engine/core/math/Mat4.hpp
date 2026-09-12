#pragma once

#include "engine/core/Types.hpp"
#include "engine/core/math/Vec.hpp"

#include <array>
#include <cmath>

/// @file Mat4.hpp
/// @brief Column-major 4x4 matrix (GPU/GLSL convention).
///
/// Minimal: identity, multiply, translation. Production aliases GLM. Column-major
/// storage matches Vulkan/OpenGL uniform expectations (no transpose on upload).

namespace engine::math {

struct Mat4 {
    // Column-major: m[col][row].
    std::array<std::array<f32, 4>, 4> m{};

    [[nodiscard]] static constexpr Mat4 identity() noexcept {
        Mat4 r{};
        r.m[0][0] = r.m[1][1] = r.m[2][2] = r.m[3][3] = 1.0f;
        return r;
    }

    [[nodiscard]] static constexpr Mat4 translation(Vec3 t) noexcept {
        Mat4 r = identity();
        r.m[3][0] = t.x;
        r.m[3][1] = t.y;
        r.m[3][2] = t.z;
        return r;
    }

    [[nodiscard]] static constexpr Mat4 scaling(Vec3 s) noexcept {
        Mat4 r{};
        r.m[0][0] = s.x;
        r.m[1][1] = s.y;
        r.m[2][2] = s.z;
        r.m[3][3] = 1.0f;
        return r;
    }

    /// Right-handed rotation matrices (column-major). Angles in radians.
    [[nodiscard]] static Mat4 rotationX(f32 r) noexcept {
        Mat4 m = identity();
        const f32 c = std::cos(r);
        const f32 s = std::sin(r);
        m.m[1][1] = c;  m.m[1][2] = s;
        m.m[2][1] = -s; m.m[2][2] = c;
        return m;
    }
    [[nodiscard]] static Mat4 rotationY(f32 r) noexcept {
        Mat4 m = identity();
        const f32 c = std::cos(r);
        const f32 s = std::sin(r);
        m.m[0][0] = c;  m.m[0][2] = -s;
        m.m[2][0] = s;  m.m[2][2] = c;
        return m;
    }
    [[nodiscard]] static Mat4 rotationZ(f32 r) noexcept {
        Mat4 m = identity();
        const f32 c = std::cos(r);
        const f32 s = std::sin(r);
        m.m[0][0] = c;  m.m[0][1] = s;
        m.m[1][0] = -s; m.m[1][1] = c;
        return m;
    }

    /// Euler rotation (degrees), applied Z * Y * X.
    [[nodiscard]] static Mat4 rotationEulerDegrees(Vec3 deg) noexcept {
        constexpr f32 kDegToRad = 3.14159265358979f / 180.0f;
        return rotationZ(deg.z * kDegToRad) * rotationY(deg.y * kDegToRad) *
               rotationX(deg.x * kDegToRad);
    }

    friend constexpr Mat4 operator*(const Mat4& a, const Mat4& b) noexcept {
        Mat4 r{};
        for (int c = 0; c < 4; ++c) {
            for (int row = 0; row < 4; ++row) {
                f32 sum = 0.0f;
                for (int k = 0; k < 4; ++k) {
                    sum += a.m[k][row] * b.m[c][k];
                }
                r.m[c][row] = sum;
            }
        }
        return r;
    }
};

} // namespace engine::math
