# benchmarks/CMakeLists.txt
include(FetchContent)

set(BENCHMARK_ENABLE_TESTING OFF CACHE BOOL "" FORCE)
set(BENCHMARK_ENABLE_INSTALL OFF CACHE BOOL "" FORCE)
FetchContent_Declare(
    benchmark
    URL https://github.com/google/benchmark/archive/refs/tags/v1.8.5.tar.gz
    DOWNLOAD_EXTRACT_TIMESTAMP TRUE
    FIND_PACKAGE_ARGS NAMES benchmark)
FetchContent_MakeAvailable(benchmark)

add_executable(imgproc_bench bench_blur.cpp)
target_link_libraries(imgproc_bench PRIVATE imgproc::imgproc benchmark::benchmark)
imgproc_set_warnings(imgproc_bench)
