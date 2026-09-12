#include "engine/core/Transform.hpp"
#include "engine/core/ecs/Registry.hpp"
#include "engine/physics/PhysicsWorld.hpp"

#include <gtest/gtest.h>

#include <cmath>

using namespace engine;
using namespace engine::physics;

TEST(Physics, GravityPullsBodyDown) {
    ecs::Registry r;
    PhysicsWorld  w;
    const auto    e = r.create();
    r.emplace<Transform>(e);
    r.emplace<RigidBody>(e, RigidBody{});

    const float y0 = r.get<Transform>(e).position.y;
    for (int i = 0; i < 10; ++i) {
        w.step(r, 1.0f / 60.0f);
    }
    EXPECT_LT(r.get<Transform>(e).position.y, y0);
    EXPECT_LT(r.get<RigidBody>(e).velocity.y, 0.0f);
}

TEST(Physics, StaticBodyStaysPut) {
    ecs::Registry r;
    PhysicsWorld  w;
    RigidBody     rb;
    rb.isStatic = true;
    const auto e = r.create();
    r.emplace<Transform>(e);
    r.emplace<RigidBody>(e, rb);

    w.step(r, 0.1f);
    EXPECT_FLOAT_EQ(r.get<Transform>(e).position.y, 0.0f);
}

TEST(Physics, OverlappingBoxesAreSeparated) {
    ecs::Registry r;
    PhysicsWorld  w;
    w.setGravity({0, 0, 0});

    RigidBody body;
    body.halfExtents = {0.5f, 0.5f, 0.5f};

    const auto e1 = r.create();
    r.emplace<Transform>(e1, Transform{}); // origin
    r.emplace<RigidBody>(e1, body);

    Transform t2;
    t2.position = {0.5f, 0, 0}; // overlaps e1 by 0.5 on X
    const auto e2 = r.create();
    r.emplace<Transform>(e2, t2);
    r.emplace<RigidBody>(e2, body);

    w.step(r, 0.001f);
    EXPECT_GT(w.lastContactCount(), 0u);

    const float dx = r.get<Transform>(e2).position.x - r.get<Transform>(e1).position.x;
    EXPECT_GT(std::fabs(dx), 0.5f); // pushed apart past their initial overlap
}

TEST(Physics, RaycastHitsBoxAtExpectedDistance) {
    ecs::Registry r;
    PhysicsWorld  w;
    RigidBody     rb;
    rb.isStatic    = true;
    rb.halfExtents = {1, 1, 1};
    Transform t;
    t.position = {0, 0, -10};
    const auto e = r.create();
    r.emplace<Transform>(e, t);
    r.emplace<RigidBody>(e, rb);

    const RaycastHit hit = w.raycast(r, {0, 0, 0}, {0, 0, -1}, 100.0f);
    EXPECT_TRUE(hit.hit);
    EXPECT_EQ(hit.entity, e);
    EXPECT_NEAR(hit.distance, 9.0f, 0.01f); // front face at z = -9
}

TEST(Physics, EmptyWorldStepIsSafe) {
    ecs::Registry r;
    PhysicsWorld  w;
    w.step(r, 1.0f / 60.0f); // no bodies — must not crash
    EXPECT_EQ(w.lastContactCount(), 0u);
}

TEST(Physics, RaycastWithZeroDirectionMisses) {
    ecs::Registry r;
    PhysicsWorld  w;
    RigidBody     rb;
    rb.isStatic = true;
    const auto e = r.create();
    r.emplace<Transform>(e);
    r.emplace<RigidBody>(e, rb);
    const RaycastHit hit = w.raycast(r, {0, 0, 0}, {0, 0, 0}, 10.0f); // degenerate ray
    EXPECT_FALSE(hit.hit);
}

TEST(Physics, RaycastMissesWhenOffAxis) {
    ecs::Registry r;
    PhysicsWorld  w;
    RigidBody     rb;
    rb.isStatic = true;
    Transform t;
    t.position = {100, 100, 100};
    const auto e = r.create();
    r.emplace<Transform>(e, t);
    r.emplace<RigidBody>(e, rb);

    const RaycastHit hit = w.raycast(r, {0, 0, 0}, {0, 0, -1}, 10.0f);
    EXPECT_FALSE(hit.hit);
}
