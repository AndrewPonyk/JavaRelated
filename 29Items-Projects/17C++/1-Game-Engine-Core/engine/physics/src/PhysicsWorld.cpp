#include "engine/physics/PhysicsWorld.hpp"

#include "engine/core/Transform.hpp"
#include "engine/core/ecs/Registry.hpp"

#include <algorithm>
#include <cmath>
#include <limits>

/// @file PhysicsWorld.cpp
/// @brief Fixed-step integration + sort-and-sweep AABB collision resolution.

namespace engine::physics {

void PhysicsWorld::step(ecs::Registry& registry, f32 fixedDt) {
    integrate(registry, fixedDt);
    detectAndResolve(registry);
}

void PhysicsWorld::integrate(ecs::Registry& registry, f32 dt) {
    registry.view<Transform, RigidBody>().each(
        [&](ecs::Entity /*e*/, Transform& tf, RigidBody& rb) {
            if (rb.isStatic) {
                return;
            }
            rb.velocity = rb.velocity + gravity_ * dt;
            tf.position = tf.position + rb.velocity * dt;
        });
}

void PhysicsWorld::detectAndResolve(ecs::Registry& registry) {
    lastContactCount_ = 0;

    // Gather bodies + current AABBs into the persistent scratch buffer (no alloc
    // once warmed up — keeps the step on the engine's zero-per-frame-allocation path).
    scratchBodies_.clear();
    registry.view<Transform, RigidBody>().each([&](ecs::Entity e, Transform& tf, RigidBody& rb) {
        scratchBodies_.push_back({e, &tf, &rb, AABB::fromCenterHalf(tf.position, rb.halfExtents)});
    });
    auto& bodies = scratchBodies_;

    // Broadphase: sort by min.x so we can break early once intervals stop overlapping.
    std::sort(bodies.begin(), bodies.end(),
              [](const Body& a, const Body& b) { return a.box.min.x < b.box.min.x; });

    for (usize i = 0; i < bodies.size(); ++i) {
        for (usize j = i + 1; j < bodies.size(); ++j) {
            if (bodies[j].box.min.x > bodies[i].box.max.x) {
                break; // no further X overlap possible (sorted)
            }
            Body& a = bodies[i];
            Body& b = bodies[j];
            if (a.body->isStatic && b.body->isStatic) {
                continue;
            }
            if (!a.box.overlaps(b.box)) {
                continue;
            }

            // Minimum translation vector: smallest penetration axis.
            const f32 px = std::min(a.box.max.x, b.box.max.x) - std::max(a.box.min.x, b.box.min.x);
            const f32 py = std::min(a.box.max.y, b.box.max.y) - std::max(a.box.min.y, b.box.min.y);
            const f32 pz = std::min(a.box.max.z, b.box.max.z) - std::max(a.box.min.z, b.box.min.z);
            if (px <= 0.0f || py <= 0.0f || pz <= 0.0f) {
                continue;
            }

            math::Vec3 normal{};
            f32        penetration = 0.0f;
            if (px <= py && px <= pz) {
                penetration = px;
                normal = {a.transform->position.x < b.transform->position.x ? -1.0f : 1.0f, 0, 0};
            } else if (py <= px && py <= pz) {
                penetration = py;
                normal = {0, a.transform->position.y < b.transform->position.y ? -1.0f : 1.0f, 0};
            } else {
                penetration = pz;
                normal = {0, 0, a.transform->position.z < b.transform->position.z ? -1.0f : 1.0f};
            }

            // Positional correction: push bodies apart along the normal.
            const bool       aMovable = !a.body->isStatic;
            const bool       bMovable = !b.body->isStatic;
            const f32        share    = (aMovable && bMovable) ? 0.5f : 1.0f;
            const math::Vec3 corr     = normal * (penetration * share);
            if (aMovable) {
                a.transform->position = a.transform->position + corr;
            }
            if (bMovable) {
                b.transform->position = b.transform->position - corr;
            }

            // Velocity response along the normal (with combined restitution).
            const math::Vec3 relVel         = a.body->velocity - b.body->velocity;
            const f32        velAlongNormal = math::dot(relVel, normal);
            if (velAlongNormal < 0.0f) {
                const f32        e       = std::min(a.body->restitution, b.body->restitution);
                const f32        jImpulse = -(1.0f + e) * velAlongNormal * share;
                const math::Vec3 impulse  = normal * jImpulse;
                if (aMovable) {
                    a.body->velocity = a.body->velocity + impulse;
                }
                if (bMovable) {
                    b.body->velocity = b.body->velocity - impulse;
                }
            }

            // Refresh AABBs after moving so subsequent pairs see updated positions.
            a.box = AABB::fromCenterHalf(a.transform->position, a.body->halfExtents);
            b.box = AABB::fromCenterHalf(b.transform->position, b.body->halfExtents);
            ++lastContactCount_;
        }
    }
}

RaycastHit PhysicsWorld::raycast(ecs::Registry& registry, math::Vec3 origin, math::Vec3 direction,
                                 f32 maxDist) const {
    RaycastHit best;
    best.distance = maxDist;

    // Normalize the direction; guard against a zero ray.
    const f32 len = math::length(direction);
    if (len <= 0.0f) {
        return best;
    }
    const math::Vec3 dir = direction * (1.0f / len);

    registry.view<Transform, RigidBody>().each([&](ecs::Entity e, Transform& tf, RigidBody& rb) {
        const AABB box = AABB::fromCenterHalf(tf.position, rb.halfExtents);

        // Slab method.
        f32 tmin = 0.0f;
        f32 tmax = best.distance;
        bool   miss = false;
        const f32 o[3] = {origin.x, origin.y, origin.z};
        const f32 d[3] = {dir.x, dir.y, dir.z};
        const f32 bmin[3] = {box.min.x, box.min.y, box.min.z};
        const f32 bmax[3] = {box.max.x, box.max.y, box.max.z};
        for (int axis = 0; axis < 3; ++axis) {
            if (std::abs(d[axis]) < 1e-8f) {
                if (o[axis] < bmin[axis] || o[axis] > bmax[axis]) {
                    miss = true;
                    break;
                }
            } else {
                f32 t1 = (bmin[axis] - o[axis]) / d[axis];
                f32 t2 = (bmax[axis] - o[axis]) / d[axis];
                if (t1 > t2) {
                    std::swap(t1, t2);
                }
                tmin = std::max(tmin, t1);
                tmax = std::min(tmax, t2);
                if (tmin > tmax) {
                    miss = true;
                    break;
                }
            }
        }
        if (!miss && tmin <= best.distance) {
            best.hit      = true;
            best.entity   = e;
            best.distance = tmin;
            best.point    = origin + dir * tmin;
        }
    });

    return best;
}

} // namespace engine::physics
