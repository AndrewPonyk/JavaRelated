// src/filters/gaussian_blur.cpp
// CPU separable Gaussian blur (pure C++) + dispatch entry point.
#include "imgproc/filters/gaussian_blur.hpp"

#include <cmath>
#include <vector>

#include "imgproc/cuda/cuda_utils.hpp"

namespace imgproc::filters {

#ifdef IMGPROC_WITH_CUDA
core::Status gaussianBlurCuda(const core::Image& src, core::Image& dst,
                              const GaussianBlurParams& params);
#endif

namespace {

std::vector<float> makeKernel(int ksize, float sigma) {
    if (sigma <= 0.0F) {
        // OpenCV's heuristic when sigma is not provided.
        sigma = 0.3F * (static_cast<float>(ksize - 1) * 0.5F - 1.0F) + 0.8F;
    }
    const int radius = ksize / 2;
    std::vector<float> k(static_cast<std::size_t>(ksize));
    float sum = 0.0F;
    for (int i = -radius; i <= radius; ++i) {
        const float v = std::exp(-static_cast<float>(i * i) / (2.0F * sigma * sigma));
        k[static_cast<std::size_t>(i + radius)] = v;
        sum += v;
    }
    for (float& v : k) {
        v /= sum;  // normalize so weights sum to 1
    }
    return k;
}

int clampIndex(int v, int lo, int hi) noexcept {
    return v < lo ? lo : (v > hi ? hi : v);
}

core::Status gaussianBlurCpu(const core::Image& src, core::Image& dst,
                             const GaussianBlurParams& params) {
    const int h = src.height();
    const int w = src.width();
    const int c = src.channels();
    const int radius = params.kernel_size / 2;
    const std::vector<float> kernel = makeKernel(params.kernel_size, params.sigma);

    // Horizontal pass: src -> tmp (float).
    std::vector<float> tmp(static_cast<std::size_t>(h) * w * c, 0.0F);
    for (int y = 0; y < h; ++y) {
        for (int x = 0; x < w; ++x) {
            for (int ch = 0; ch < c; ++ch) {
                float acc = 0.0F;
                for (int t = -radius; t <= radius; ++t) {
                    const int xx = clampIndex(x + t, 0, w - 1);
                    acc += kernel[static_cast<std::size_t>(t + radius)] *
                           static_cast<float>(src.at(y, xx, ch));
                }
                tmp[(static_cast<std::size_t>(y) * w + x) * c + ch] = acc;
            }
        }
    }

    // Vertical pass: tmp -> dst (uint8).
    dst = core::Image(h, w, src.format());
    for (int y = 0; y < h; ++y) {
        for (int x = 0; x < w; ++x) {
            for (int ch = 0; ch < c; ++ch) {
                float acc = 0.0F;
                for (int t = -radius; t <= radius; ++t) {
                    const int yy = clampIndex(y + t, 0, h - 1);
                    acc += kernel[static_cast<std::size_t>(t + radius)] *
                           tmp[(static_cast<std::size_t>(yy) * w + x) * c + ch];
                }
                const float r = acc < 0.0F ? 0.0F : (acc > 255.0F ? 255.0F : acc);
                dst.at(y, x, ch) = static_cast<std::uint8_t>(r + 0.5F);
            }
        }
    }
    return core::Status::Ok();
}

}  // namespace

core::Status gaussianBlur(const core::Image& src, core::Image& dst,
                          const GaussianBlurParams& params) {
    if (src.empty()) {
        return core::InvalidArgument("gaussianBlur: source image is empty");
    }
    if (params.kernel_size < 1 || (params.kernel_size % 2) == 0) {
        return core::InvalidArgument("gaussianBlur: kernel_size must be odd >= 1");
    }
    if (src.format() == core::PixelFormat::kRgbf32) {
        return core::Unsupported("gaussianBlur: float images not supported");
    }

#ifdef IMGPROC_WITH_CUDA
    if (params.device == core::Device::kCuda && cuda::isAvailable()) {
        return gaussianBlurCuda(src, dst, params);
    }
#endif
    return gaussianBlurCpu(src, dst, params);
}

}  // namespace imgproc::filters
