#include "engine/core/ecs/CommandBuffer.hpp"
#include "engine/core/ecs/Registry.hpp"

#include <gtest/gtest.h>

#include <vector>

using namespace engine::ecs;

namespace {
struct Tag {
    int v = 0;
};
} // namespace

TEST(CommandBuffer, DefersCreateAndComponentAdd) {
    Registry      r;
    CommandBuffer cb;
    cb.create([](Registry& reg, Entity e) { reg.emplace<Tag>(e, Tag{7}); });

    EXPECT_EQ(r.aliveCount(), 0u); // not applied yet
    EXPECT_EQ(cb.pending(), 1u);

    cb.flush(r);
    EXPECT_EQ(r.aliveCount(), 1u);
    EXPECT_TRUE(cb.empty());
}

TEST(CommandBuffer, SafeStructuralChangeDuringIteration) {
    Registry r;
    for (int i = 0; i < 5; ++i) {
        const Entity e = r.create();
        r.emplace<Tag>(e, Tag{i});
    }

    CommandBuffer cb;
    // Queue destruction of even-valued entities while iterating the view.
    r.view<Tag>().each([&](Entity e, Tag& t) {
        if (t.v % 2 == 0) {
            cb.destroy(e);
        }
    });
    EXPECT_EQ(r.aliveCount(), 5u); // nothing destroyed during iteration

    cb.flush(r);
    EXPECT_EQ(r.aliveCount(), 2u); // values 1 and 3 remain (0,2,4 destroyed)
}

TEST(CommandBuffer, DeferredRemoveComponent) {
    Registry     r;
    const Entity e = r.create();
    r.emplace<Tag>(e, Tag{1});

    CommandBuffer cb;
    cb.remove<Tag>(e);
    EXPECT_TRUE(r.has<Tag>(e));
    cb.flush(r);
    EXPECT_FALSE(r.has<Tag>(e));
}
