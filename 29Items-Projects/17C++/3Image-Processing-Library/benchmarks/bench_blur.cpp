// benchmarks/bench_blur.cpp
// google/benchmark micro-benchmark comparing CPU vs CUDA Gaussian blur.
#include <benchmark/benchmark.h>

#include "imgproc/core/image.hpp"
#include "imgproc/filters/gaussian_blur.hpp"

namespace {

using imgproc::core::Device;
using imgproc::core::Image;
using imgproc::core::PixelFormat;
using imgproc::filters::GaussianBlurParams;
using imgproc::filters::gaussianBlur;

void BM_GaussianBlur(benchmark::State& state, Device device) {
    const auto side = static_cast<int>(state.range(0));
    Image src(side, side, PixelFormat::kBgr8);
    Image dst;
    GaussianBlurParams params{.kernel_size = 5, .sigma = 1.5F, .device = device};

    for (auto _ : state) {
        benchmark::DoNotOptimize(gaussianBlur(src, dst, params));
    }
    state.SetItemsProcessed(state.iterations() *
                            static_cast<int64_t>(side) * side);
}

}  // namespace

BENCHMARK_CAPTURE(BM_GaussianBlur, cpu, Device::kCpu)
    ->Arg(256)->Arg(1024)->Arg(2048);
BENCHMARK_CAPTURE(BM_GaussianBlur, cuda, Device::kCuda)
    ->Arg(256)->Arg(1024)->Arg(2048);

BENCHMARK_MAIN();
