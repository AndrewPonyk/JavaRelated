// imgproc/filters/gaussian_blur.hpp
// Gaussian blur with automatic CPU (OpenCV) / GPU (CUDA kernel) dispatch.
#pragma once

#include "imgproc/core/image.hpp"
#include "imgproc/core/status.hpp"
#include "imgproc/core/types.hpp"

namespace imgproc::filters {

struct GaussianBlurParams {
    int kernel_size{5};     ///< odd, >= 1
    float sigma{1.0F};      ///< standard deviation; <=0 derives from kernel_size
    core::Device device{core::Device::kCpu};
};

/// Apply a Gaussian blur to `src`, writing to `dst`.
/// Dispatches to a CUDA kernel when params.device == kCuda and a device exists,
/// otherwise falls back to the OpenCV CPU path.
[[nodiscard]] core::Status gaussianBlur(const core::Image& src,
                                        core::Image& dst,
                                        const GaussianBlurParams& params);

}  // namespace imgproc::filters
