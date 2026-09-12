#pragma once

#include "engine/core/Types.hpp"
#include "engine/core/ecs/Entity.hpp"
#include "engine/core/math/Vec.hpp"

#include <vector>

/// @file PhysicsWorld.hpp
/// @brief Rigid-body simulation with AABB colliders, driven on a fixed timestep.
///
/// Runs as an ECS system: reads {Transform, RigidBody}, integrates motion
/// (semi-implicit Euler), resolves AABB overlaps (sort-and-sweep broadphase +
/// minimum-translation-vector resolution with restitution), and writes results back.
/// Production swaps the solver for Jolt; the ECS-system shape is identical.

namespace engine {
struct Transform; // forward decl: PhysicsWorld scratch holds Transform* (pointer only)
}
namespace engine::ecs {
class Registry;
}

namespace engine::physics {

/// Box rigid body. `halfExtents` defines the collision AABB around the Transform.
struct RigidBody {
    math::Vec3 velocity{};
    f32        mass        = 1.0f;
    f32        restitution = 0.2f;  // 0 = inelastic, 1 = perfectly elastic
    bool       isStatic    = false; // static bodies don't move but do collide
    math::Vec3 halfExtents{0.5f, 0.5f, 0.5f};
};

struct AABB {
    math::Vec3 min{};
    math::Vec3 max{};

    [[nodiscard]] static AABB fromCenterHalf(math::Vec3 center, math::Vec3 half) {
        return {{center.x - half.x, center.y - half.y, center.z - half.z},
                {center.x + half.x, center.y + half.y, center.z + half.z}};
    }
    [[nodiscard]] bool overlaps(const AABB& o) const {
        return min.x <= o.max.x && max.x >= o.min.x && min.y <= o.max.y && max.y >= o.min.y &&
               min.z <= o.max.z && max.z >= o.min.z;
    }
};

struct RaycastHit {
    ecs::Entity entity{};
    f32         distance = 0.0f;
    math::Vec3  point{};
    bool        hit = false;
};

class PhysicsWorld {
public:
    void setGravity(math::Vec3 g) noexcept { gravity_ = g; }
    [[nodiscard]] math::Vec3 gravity() const noexcept { return gravity_; }

    /// Advance the simulation by one fixed step (reads/writes ECS components).
    void step(ecs::Registry& registry, f32 fixedDt);

    /// Closest-hit ray query against current collider bounds (ray vs AABB slabs).
    [[nodiscard]] RaycastHit raycast(ecs::Registry& registry, math::Vec3 origin,
                                     math::Vec3 direction, f32 maxDist) const;

    /// Contacts resolved during the last step() (useful for diagnostics/tests).
    [[nodiscard]] usize lastContactCount() const noexcept { return lastContactCount_; }

private:
    /// Broadphase scratch entry. Reused across steps to avoid per-frame allocation.
    struct Body {
        ecs::Entity entity;
        Transform*  transform = nullptr;
        RigidBody*  body      = nullptr;
        AABB        box;
    };

    void integrate(ecs::Registry& registry, f32 dt);
    void detectAndResolve(ecs::Registry& registry);

    math::Vec3        gravity_{0.0f, -9.81f, 0.0f};
    usize             lastContactCount_ = 0;
    std::vector<Body> scratchBodies_; // persistent buffer (no per-frame heap churn)
};

} // namespace engine::physics
