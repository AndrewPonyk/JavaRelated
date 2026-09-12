#include "engine/core/ecs/SparseSet.hpp"

#include <gtest/gtest.h>

using namespace engine::ecs;

TEST(SparseSet, InsertContainsRemove) {
    SparseSet set;
    const Entity a = makeEntity(5, 0);
    const Entity b = makeEntity(9, 0);

    EXPECT_FALSE(set.contains(a));
    set.insert(a);
    set.insert(b);
    EXPECT_TRUE(set.contains(a));
    EXPECT_TRUE(set.contains(b));
    EXPECT_EQ(set.size(), 2u);

    set.remove(a);
    EXPECT_FALSE(set.contains(a));
    EXPECT_TRUE(set.contains(b));
    EXPECT_EQ(set.size(), 1u);
}

TEST(SparseSet, SwapAndPopKeepsDenseContiguous) {
    SparseSet set;
    const Entity a = makeEntity(1, 0);
    const Entity b = makeEntity(2, 0);
    const Entity c = makeEntity(3, 0);
    set.insert(a);
    set.insert(b);
    set.insert(c);

    // Removing the middle element should move the last into its slot.
    set.remove(b);
    EXPECT_EQ(set.size(), 2u);
    EXPECT_TRUE(set.contains(a));
    EXPECT_TRUE(set.contains(c));
    EXPECT_FALSE(set.contains(b));

    // Dense array holds exactly the survivors.
    const auto& dense = set.entities();
    ASSERT_EQ(dense.size(), 2u);
    EXPECT_TRUE((dense[0] == a || dense[1] == a));
    EXPECT_TRUE((dense[0] == c || dense[1] == c));
}

TEST(SparseSet, GenerationDistinguishesRecycledIndex) {
    SparseSet set;
    const Entity oldGen = makeEntity(7, 0);
    const Entity newGen = makeEntity(7, 1); // same index, bumped generation
    set.insert(newGen);
    EXPECT_TRUE(set.contains(newGen));
    EXPECT_FALSE(set.contains(oldGen)); // stale handle not a member
}

TEST(SparseSet, ClearEmpties) {
    SparseSet set;
    set.insert(makeEntity(0, 0));
    set.insert(makeEntity(1, 0));
    set.clear();
    EXPECT_TRUE(set.empty());
    EXPECT_EQ(set.size(), 0u);
}
