#include "engine/core/memory/PoolAllocator.hpp"

#include <gtest/gtest.h>

#include <cstdint>

using engine::memory::PoolAllocator;

TEST(PoolAllocator, HandsOutDistinctBlocks) {
    PoolAllocator pool(64, 4);
    EXPECT_EQ(pool.capacityBlocks(), 4u);
    EXPECT_EQ(pool.freeBlocks(), 4u);

    void* a = pool.allocate(64);
    void* b = pool.allocate(32); // smaller-than-block is fine
    ASSERT_NE(a, nullptr);
    ASSERT_NE(b, nullptr);
    EXPECT_NE(a, b);
    EXPECT_EQ(pool.freeBlocks(), 2u);
}

TEST(PoolAllocator, ReturnsNullWhenExhausted) {
    PoolAllocator pool(32, 2);
    EXPECT_NE(pool.allocate(32), nullptr);
    EXPECT_NE(pool.allocate(32), nullptr);
    EXPECT_EQ(pool.allocate(32), nullptr); // pool is full
}

TEST(PoolAllocator, DeallocateReturnsBlockToPool) {
    PoolAllocator pool(64, 2);
    void* a = pool.allocate(64);
    void* b = pool.allocate(64);
    ASSERT_NE(a, nullptr);
    ASSERT_NE(b, nullptr);
    EXPECT_EQ(pool.allocate(64), nullptr);

    pool.deallocate(a);
    EXPECT_EQ(pool.freeBlocks(), 1u);
    void* reused = pool.allocate(64);
    EXPECT_EQ(reused, a); // most-recently-freed block is reused (LIFO free list)
}

TEST(PoolAllocator, TracksBytesInUse) {
    PoolAllocator pool(64, 3);
    EXPECT_EQ(pool.bytesInUse(), 0u);
    void* a = pool.allocate(64);
    EXPECT_EQ(pool.bytesInUse(), pool.blockSize());
    pool.deallocate(a);
    EXPECT_EQ(pool.bytesInUse(), 0u);
}

TEST(PoolAllocator, RespectsAlignment) {
    constexpr std::size_t kAlign = 64;
    PoolAllocator         pool(16, 8, kAlign);
    for (int i = 0; i < 4; ++i) {
        void* p = pool.allocate(16, kAlign);
        ASSERT_NE(p, nullptr);
        EXPECT_EQ(reinterpret_cast<std::uintptr_t>(p) % kAlign, 0u);
    }
}
