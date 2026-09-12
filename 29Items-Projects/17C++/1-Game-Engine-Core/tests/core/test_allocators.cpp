#include "engine/core/memory/LinearAllocator.hpp"

#include <gtest/gtest.h>

#include <array>
#include <cstdint>

using engine::memory::LinearAllocator;

TEST(LinearAllocator, AllocatesSequentially) {
    alignas(64) std::array<unsigned char, 256> buf{};
    LinearAllocator                            a(buf.data(), buf.size());
    void*                                      p1 = a.allocate(16, 16);
    void*                                      p2 = a.allocate(16, 16);
    EXPECT_NE(p1, nullptr);
    EXPECT_NE(p2, nullptr);
    EXPECT_NE(p1, p2);
    EXPECT_GE(a.bytesInUse(), 32u);
}

TEST(LinearAllocator, ResetRewindsToStart) {
    alignas(64) std::array<unsigned char, 128> buf{};
    LinearAllocator                            a(buf.data(), buf.size());
    void*                                      p1 = a.allocate(32);
    a.reset();
    EXPECT_EQ(a.bytesInUse(), 0u);
    void* p2 = a.allocate(32);
    EXPECT_EQ(p1, p2); // same address reused after reset
}

TEST(LinearAllocator, ReturnsNullWhenExhausted) {
    alignas(64) std::array<unsigned char, 32> buf{};
    LinearAllocator                           a(buf.data(), buf.size());
    EXPECT_NE(a.allocate(32), nullptr);
    EXPECT_EQ(a.allocate(1), nullptr);
}

TEST(LinearAllocator, RespectsAlignment) {
    alignas(64) std::array<unsigned char, 256> buf{};
    LinearAllocator                            a(buf.data(), buf.size());
    a.allocate(1); // deliberately misalign the offset
    void* p = a.allocate(8, 64);
    ASSERT_NE(p, nullptr);
    EXPECT_EQ(reinterpret_cast<std::uintptr_t>(p) % 64, 0u);
}
