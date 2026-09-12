// src/filters/gaussian_blur.cu
// CUDA implementation of a separable Gaussian blur. Compiled only when
// IMGPROC_WITH_CUDA is enabled (optional accelerated backend).
#include "imgproc/filters/gaussian_blur.hpp"

#ifdef IMGPROC_WITH_CUDA
#include <cuda_runtime.h>

#include <cmath>
#include <vector>

namespace imgproc::filters {

namespace {

__device__ __forceinline__ int clampi(int v, int lo, int hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

// One pass of a separable convolution. `horizontal` selects the axis.
__global__ void convPass(const float* src, float* dst, int w, int h, int c,
                         const float* kernel, int radius, bool horizontal) {
    const int x = blockIdx.x * blockDim.x + threadIdx.x;
    const int y = blockIdx.y * blockDim.y + threadIdx.y;
    if (x >= w || y >= h) {
        return;
    }
    for (int ch = 0; ch < c; ++ch) {
        float acc = 0.0f;
        for (int t = -radius; t <= radius; ++t) {
            const int xx = horizontal ? clampi(x + t, 0, w - 1) : x;
            const int yy = horizontal ? y : clampi(y + t, 0, h - 1);
            acc += kernel[t + radius] *
                   src[(static_cast<long>(yy) * w + xx) * c + ch];
        }
        dst[(static_cast<long>(y) * w + x) * c + ch] = acc;
    }
}

std::vector<float> makeKernel(int ksize, float sigma) {
    if (sigma <= 0.0f) {
        sigma = 0.3f * ((ksize - 1) * 0.5f - 1.0f) + 0.8f;
    }
    const int radius = ksize / 2;
    std::vector<float> k(ksize);
    float sum = 0.0f;
    for (int i = -radius; i <= radius; ++i) {
        const float v = std::exp(-static_cast<float>(i * i) / (2.0f * sigma * sigma));
        k[i + radius] = v;
        sum += v;
    }
    for (float& v : k) v /= sum;
    return k;
}

}  // namespace

core::Status gaussianBlurCuda(const core::Image& src, core::Image& dst,
                              const GaussianBlurParams& params) {
    const int w = src.width();
    const int h = src.height();
    const int c = src.channels();
    const int radius = params.kernel_size / 2;
    const std::size_t n = static_cast<std::size_t>(w) * h * c;

    const std::vector<float> kernel = makeKernel(params.kernel_size, params.sigma);

    // Host float copy of the input.
    std::vector<float> h_in(n);
    for (std::size_t i = 0; i < n; ++i) {
        h_in[i] = static_cast<float>(src.data()[i]);
    }

    float *d_a = nullptr, *d_b = nullptr, *d_k = nullptr;
    IMGPROC_CUDA_CHECK(cudaMalloc(&d_a, n * sizeof(float)));
    IMGPROC_CUDA_CHECK(cudaMalloc(&d_b, n * sizeof(float)));
    IMGPROC_CUDA_CHECK(cudaMalloc(&d_k, kernel.size() * sizeof(float)));
    IMGPROC_CUDA_CHECK(cudaMemcpy(d_a, h_in.data(), n * sizeof(float), cudaMemcpyHostToDevice));
    IMGPROC_CUDA_CHECK(cudaMemcpy(d_k, kernel.data(), kernel.size() * sizeof(float),
                       cudaMemcpyHostToDevice));

    const dim3 block(16, 16);
    const dim3 grid((w + block.x - 1) / block.x, (h + block.y - 1) / block.y);
    convPass<<<grid, block>>>(d_a, d_b, w, h, c, d_k, radius, true);   // horizontal
    convPass<<<grid, block>>>(d_b, d_a, w, h, c, d_k, radius, false);  // vertical
    IMGPROC_CUDA_CHECK(cudaGetLastError());
    IMGPROC_CUDA_CHECK(cudaDeviceSynchronize());

    std::vector<float> h_out(n);
    IMGPROC_CUDA_CHECK(cudaMemcpy(h_out.data(), d_a, n * sizeof(float), cudaMemcpyDeviceToHost));
    cudaFree(d_a);
    cudaFree(d_b);
    cudaFree(d_k);

    dst = core::Image(h, w, src.format());
    for (std::size_t i = 0; i < n; ++i) {
        const float v = h_out[i] < 0.0f ? 0.0f : (h_out[i] > 255.0f ? 255.0f : h_out[i]);
        dst.data()[i] = static_cast<std::uint8_t>(v + 0.5f);
    }
    return core::Status::Ok();
}

}  // namespace imgproc::filters
#endif  // IMGPROC_WITH_CUDA
