#include "engine/core/ecs/Registry.hpp"

#include <benchmark/benchmark.h>

/// @file bench_ecs.cpp
/// @brief Throughput of ECS iteration — the hot path the data-oriented design exists
/// to optimize. CI flags regressions beyond a threshold (see docs/TECH-NOTES.md §3.2).

using namespace engine::ecs;

namespace {
struct Position {
    float x = 0, y = 0, z = 0;
};
struct Velocity {
    float dx = 1, dy = 1, dz = 1;
};
} // namespace

/// Build a registry of N entities all owning {Position, Velocity}.
static Registry makeWorld(std::size_t n) {
    Registry r;
    for (std::size_t i = 0; i < n; ++i) {
        const Entity e = r.create();
        r.emplace<Position>(e, Position{});
        r.emplace<Velocity>(e, Velocity{});
    }
    return r;
}

static void BM_ViewIteration(benchmark::State& state) {
    Registry r = makeWorld(static_cast<std::size_t>(state.range(0)));
    for (auto _ : state) {
        r.view<Position, Velocity>().each([](Entity, Position& p, Velocity& v) {
            p.x += v.dx;
            p.y += v.dy;
            p.z += v.dz;
        });
        benchmark::ClobberMemory();
    }
    state.SetItemsProcessed(state.iterations() * state.range(0));
}
BENCHMARK(BM_ViewIteration)->Arg(1'000)->Arg(100'000)->Arg(1'000'000);

static void BM_EntityCreateDestroy(benchmark::State& state) {
    Registry r;
    for (auto _ : state) {
        const Entity e = r.create();
        benchmark::DoNotOptimize(e);
        r.destroy(e);
    }
}
BENCHMARK(BM_EntityCreateDestroy);

BENCHMARK_MAIN();
