// ============================================================================
//  tests/benchmark/bench_lockfree.cpp
//  Microbenchmarks for the hot-path primitives. CI compares results against a
//  stored baseline and FAILS the build on a latency/throughput regression.
// ============================================================================
#include "core/lockfree/SPSCQueue.hpp"
#include "core/memory/ObjectPool.hpp"

#include <benchmark/benchmark.h>

#include <thread>

// --- SPSC round-trip throughput --------------------------------------------
static void BM_SPSC_PingPong(benchmark::State& state) {
    rts::lockfree::SPSCQueue<std::int64_t> q(1024);
    for (auto _ : state) {
        q.tryPush(42);
        auto v = q.tryPop();
        benchmark::DoNotOptimize(v);
    }
}
BENCHMARK(BM_SPSC_PingPong);

// --- Object pool acquire/release (must be allocation-free) ------------------
static void BM_ObjectPool_AcquireRelease(benchmark::State& state) {
    struct Order { char data[64]; };
    rts::memory::ObjectPool<Order> pool(4096);
    for (auto _ : state) {
        auto h = pool.acquire();
        benchmark::DoNotOptimize(h.get());
        // h returns to the pool here (no free() call hits the allocator)
    }
}
BENCHMARK(BM_ObjectPool_AcquireRelease);

// --- Cross-thread SPSC latency ---------------------------------------------
static void BM_SPSC_CrossThread(benchmark::State& state) {
    rts::lockfree::SPSCQueue<std::int64_t> q(4096);
    std::atomic<bool> stop{false};
    std::thread consumer([&] {
        while (!stop.load(std::memory_order_acquire)) {
            auto v = q.tryPop();
            benchmark::DoNotOptimize(v);
        }
    });
    for (auto _ : state) {
        while (!q.tryPush(1)) { /* spin */ }
    }
    stop.store(true, std::memory_order_release);
    consumer.join();
}
BENCHMARK(BM_SPSC_CrossThread);

BENCHMARK_MAIN();
