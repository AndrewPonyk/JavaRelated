#pragma once

#include "engine/core/Types.hpp"
#include "engine/core/math/Mat4.hpp"
#include "engine/core/math/Vec.hpp"

/// @file Transform.hpp
/// @brief Fundamental spatial component shared by physics, rendering, and scene.
///
/// Lives in core because it is the common denominator of the 3D data model — keeping
/// it here lets every higher module reference one Transform type without inverting
/// the dependency layering (everything depends on core; core depends on nothing).

namespace engine {

/// Trivially-copyable POD component (data-oriented friendly).
struct Transform {
    math::Vec3 position{0.0f, 0.0f, 0.0f};
    math::Vec3 eulerDegrees{0.0f, 0.0f, 0.0f}; // a quaternion type is a future refinement
    math::Vec3 scale{1.0f, 1.0f, 1.0f};

    /// Compose the local model matrix as Translation * Rotation * Scale.
    [[nodiscard]] math::Mat4 localMatrix() const noexcept {
        return math::Mat4::translation(position) * math::Mat4::rotationEulerDegrees(eulerDegrees) *
               math::Mat4::scaling(scale);
    }
};

} // namespace engine
