// src/core/tensor.cpp
#include "imgproc/core/tensor.hpp"

#include <algorithm>
#include <vector>

namespace imgproc::core {

// Host data is a std::vector<float>. A device buffer is only allocated on CUDA
// builds (kept as an opaque pointer; see the CUDA-enabled translation units).
struct Tensor::Impl {
    std::vector<float> host;
    void* device{nullptr};
};

Tensor::Tensor() : impl_(std::make_unique<Impl>()) {}

Tensor::Tensor(std::vector<int> shape, Device device)
    : impl_(std::make_unique<Impl>()), shape_(std::move(shape)), device_(device) {
    impl_->host.assign(numel(), 0.0F);
    // NOTE: on Device::kCuda builds the accelerated paths mirror `host` to
    // `impl_->device`; the CPU fallback keeps everything host-resident.
}

Tensor::~Tensor() = default;
Tensor::Tensor(Tensor&&) noexcept = default;
Tensor& Tensor::operator=(Tensor&&) noexcept = default;

float* Tensor::host_data() noexcept {
    return impl_ ? impl_->host.data() : nullptr;
}
const float* Tensor::host_data() const noexcept {
    return impl_ ? impl_->host.data() : nullptr;
}

void Tensor::fill(float value) {
    if (impl_) {
        std::fill(impl_->host.begin(), impl_->host.end(), value);
    }
}

Status Tensor::to(Device target, Tensor& out) const {
    if (!impl_) {
        return InvalidArgument("Tensor::to: source tensor is empty");
    }
    Tensor dst(shape_, target);
    // Host-to-host copy (the CPU fallback). Device transfers happen in the
    // CUDA translation units when compiled with IMGPROC_WITH_CUDA.
    std::copy(impl_->host.begin(), impl_->host.end(), dst.impl_->host.begin());
    out = std::move(dst);
    return Status::Ok();
}

}  // namespace imgproc::core
