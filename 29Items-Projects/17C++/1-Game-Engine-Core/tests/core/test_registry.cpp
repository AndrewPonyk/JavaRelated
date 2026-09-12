#include "engine/core/ecs/Registry.hpp"

#include <gtest/gtest.h>

using namespace engine::ecs;

namespace {
struct Position {
    float x = 0, y = 0, z = 0;
};
struct Velocity {
    float dx = 0, dy = 0;
};
} // namespace

TEST(Registry, CreateAndValidate) {
    Registry r;
    const Entity e = r.create();
    EXPECT_TRUE(r.valid(e));
    EXPECT_EQ(r.aliveCount(), 1u);
}

TEST(Registry, EmplaceGetHas) {
    Registry r;
    const Entity e = r.create();
    r.emplace<Position>(e, Position{1.0f, 2.0f, 3.0f});

    ASSERT_TRUE(r.has<Position>(e));
    EXPECT_FLOAT_EQ(r.get<Position>(e).x, 1.0f);
    EXPECT_FLOAT_EQ(r.get<Position>(e).z, 3.0f);
    EXPECT_FALSE(r.has<Velocity>(e));
}

TEST(Registry, PatchUpdatesInPlace) {
    Registry r;
    const Entity e = r.create();
    r.emplace<Position>(e, Position{0, 0, 0});
    r.patch<Position>(e, [](Position& p) { p.y = 42.0f; });
    EXPECT_FLOAT_EQ(r.get<Position>(e).y, 42.0f);
}

TEST(Registry, RemoveComponent) {
    Registry r;
    const Entity e = r.create();
    r.emplace<Position>(e, Position{});
    ASSERT_TRUE(r.has<Position>(e));
    r.remove<Position>(e);
    EXPECT_FALSE(r.has<Position>(e));
}

TEST(Registry, DestroyInvalidatesHandleAndRecyclesSlot) {
    Registry r;
    const Entity e = r.create();
    const auto   idx = e.index();
    r.destroy(e);

    EXPECT_FALSE(r.valid(e)); // stale handle rejected by generation check
    EXPECT_EQ(r.aliveCount(), 0u);

    const Entity e2 = r.create();
    EXPECT_EQ(e2.index(), idx);                  // slot recycled
    EXPECT_NE(e2.generation(), e.generation());  // but generation bumped
    EXPECT_TRUE(r.valid(e2));
}

TEST(Registry, DestroyAlsoRemovesComponents) {
    Registry r;
    const Entity e = r.create();
    r.emplace<Position>(e, Position{1, 1, 1});
    r.destroy(e);
    const Entity e2 = r.create(); // reuses the slot
    EXPECT_FALSE(r.has<Position>(e2));
}

TEST(Registry, ViewIteratesOnlyTheIntersection) {
    Registry r;
    // 3 entities with both components, 2 with only Position.
    for (int i = 0; i < 3; ++i) {
        const Entity e = r.create();
        r.emplace<Position>(e, Position{});
        r.emplace<Velocity>(e, Velocity{1.0f, 0.0f});
    }
    for (int i = 0; i < 2; ++i) {
        const Entity e = r.create();
        r.emplace<Position>(e, Position{});
    }

    int visited = 0;
    r.view<Position, Velocity>().each([&](Entity, Position&, Velocity& v) {
        v.dx += 1.0f;
        ++visited;
    });
    EXPECT_EQ(visited, 3);
}

TEST(Registry, ViewOverEmptyRegistryIsNoop) {
    Registry r;
    int      visited = 0;
    r.view<Position, Velocity>().each([&](Entity, Position&, Velocity&) { ++visited; });
    EXPECT_EQ(visited, 0);
}

TEST(Registry, TryGetOnMissingComponentReturnsNull) {
    Registry     r;
    const Entity e = r.create();
    EXPECT_EQ(r.tryGet<Position>(e), nullptr);
    EXPECT_FALSE(r.has<Position>(e));
}

TEST(Registry, ViewWritesAreVisible) {
    Registry r;
    const Entity e = r.create();
    r.emplace<Position>(e, Position{});
    r.emplace<Velocity>(e, Velocity{2.0f, 3.0f});

    r.view<Position, Velocity>().each([](Entity, Position& p, Velocity& v) {
        p.x += v.dx;
        p.y += v.dy;
    });
    EXPECT_FLOAT_EQ(r.get<Position>(e).x, 2.0f);
    EXPECT_FLOAT_EQ(r.get<Position>(e).y, 3.0f);
}
