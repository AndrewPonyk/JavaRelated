# benchmarks — google/benchmark micro-benchmarks (ENGINE_BUILD_BENCHMARKS).

if(NOT TARGET benchmark::benchmark)
    message(WARNING "[benchmarks] google/benchmark not found — skipping benchmarks.")
    return()
endif()

add_executable(bench_ecs bench_ecs.cpp)
target_link_libraries(bench_ecs
    PRIVATE engine_project_options engine::core benchmark::benchmark
)
