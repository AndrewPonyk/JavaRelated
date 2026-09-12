// imgproc/core/tensor.hpp
// N-dimensional float32 tensor (NCHW by convention). Host storage is always
// available; device storage is used only on Device::kCuda builds. Device
// details are hidden behind a pimpl so this header pulls in no CUDA headers.
#pragma once

#include <cstddef>
#include <functional>
#include <memory>
#include <numeric>
#include <vector>

#include "imgproc/core/status.hpp"
#include "imgproc/core/types.hpp"

namespace imgproc::core {

/// A float32 tensor. On Device::kCuda the primary buffer lives in device memory.
class Tensor {
public:
    Tensor();
    Tensor(std::vector<int> shape, Device device);
    ~Tensor();

    Tensor(Tensor&&) noexcept;
    Tensor& operator=(Tensor&&) noexcept;
    Tensor(const Tensor&) = delete;
    Tensor& operator=(const Tensor&) = delete;

    [[nodiscard]] const std::vector<int>& shape() const noexcept { return shape_; }
    [[nodiscard]] Device device() const noexcept { return device_; }
    [[nodiscard]] std::size_t numel() const noexcept {
        return shape_.empty() ? 0U
                              : std::accumulate(shape_.begin(), shape_.end(),
                                                std::size_t{1}, std::multiplies<>());
    }

    /// Direct access to host-resident float data (valid when device == kCpu).
    [[nodiscard]] float* host_data() noexcept;
    [[nodiscard]] const float* host_data() const noexcept;

    /// Set every element to `value` (host tensors).
    void fill(float value);

    /// Copy this tensor to the requested device.
    [[nodiscard]] Status to(Device target, Tensor& out) const;

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
    std::vector<int> shape_{};
    Device device_{Device::kCpu};
};

}  // namespace imgproc::core
