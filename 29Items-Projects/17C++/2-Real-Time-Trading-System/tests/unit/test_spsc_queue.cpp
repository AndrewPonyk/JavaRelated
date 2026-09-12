// ============================================================================
//  tests/unit/test_spsc_queue.cpp
//  Unit + light stress tests for the SPSC ring buffer.
// ============================================================================
#include "core/lockfree/SPSCQueue.hpp"

#include <gtest/gtest.h>

#include <thread>
#include <vector>

using rts::lockfree::SPSCQueue;

TEST(SPSCQueue, CapacityRoundsUpToPowerOfTwo) {
    SPSCQueue<int> q(100);
    EXPECT_EQ(q.capacity(), 128u);
}

TEST(SPSCQueue, PushPopFifoOrder) {
    SPSCQueue<int> q(8);
    for (int i = 0; i < 5; ++i) ASSERT_TRUE(q.tryPush(i));
    for (int i = 0; i < 5; ++i) {
        auto v = q.tryPop();
        ASSERT_TRUE(v.has_value());
        EXPECT_EQ(*v, i);
    }
    EXPECT_FALSE(q.tryPop().has_value());  // empty
}

TEST(SPSCQueue, RejectsWhenFull) {
    SPSCQueue<int> q(2);  // capacity 2
    EXPECT_TRUE(q.tryPush(1));
    EXPECT_TRUE(q.tryPush(2));
    EXPECT_FALSE(q.tryPush(3));  // full
}

TEST(SPSCQueue, ConcurrentProducerConsumerPreservesAllItems) {
    constexpr int N = 1'000'000;
    SPSCQueue<int> q(1024);

    std::thread producer([&] {
        for (int i = 0; i < N;) {
            if (q.tryPush(i)) ++i;  // spin on full
        }
    });

    long long sum = 0;
    int received = 0;
    while (received < N) {
        if (auto v = q.tryPop()) { sum += *v; ++received; }
    }
    producer.join();

    const long long expected = static_cast<long long>(N - 1) * N / 2;
    EXPECT_EQ(received, N);
    EXPECT_EQ(sum, expected);  // no lost or duplicated items
}

// Note: also run under ThreadSanitizer in CI to validate memory ordering.
