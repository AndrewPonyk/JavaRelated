// src/cuda/cuda_utils.cpp
// CUDA helper implementations. When IMGPROC_WITH_CUDA is off, these degrade to
// "no device available" so callers transparently fall back to CPU.
#include "imgproc/cuda/cuda_utils.hpp"

#ifdef IMGPROC_WITH_CUDA
#include <cuda_runtime.h>

#include <string>
#endif

namespace imgproc::cuda {

bool isAvailable() noexcept { return deviceCount() > 0; }

int deviceCount() noexcept {
#ifdef IMGPROC_WITH_CUDA
    int count = 0;
    if (cudaGetDeviceCount(&count) != cudaSuccess) {
        return 0;
    }
    return count;
#else
    return 0;
#endif
}

core::Status deviceInfo(int index, std::string& out_description) {
#ifdef IMGPROC_WITH_CUDA
    cudaDeviceProp prop{};
    if (cudaGetDeviceProperties(&prop, index) != cudaSuccess) {
        return core::CudaError("cudaGetDeviceProperties failed");
    }
    const std::size_t total_mib = prop.totalGlobalMem / (1024U * 1024U);
    out_description = std::string(prop.name) + " (SM " +
                      std::to_string(prop.major) + "." + std::to_string(prop.minor) +
                      ", " + std::to_string(prop.multiProcessorCount) + " SMs, " +
                      std::to_string(total_mib) + " MiB)";
    return core::Status::Ok();
#else
    (void)index;
    (void)out_description;
    return core::Unsupported("built without CUDA support");
#endif
}

Stream::Stream() {
#ifdef IMGPROC_WITH_CUDA
    cudaStream_t s = nullptr;
    if (cudaStreamCreate(&s) == cudaSuccess) {
        handle_ = s;
    }
#endif
}

Stream::~Stream() {
#ifdef IMGPROC_WITH_CUDA
    if (handle_ != nullptr) {
        cudaStreamDestroy(static_cast<cudaStream_t>(handle_));
    }
#endif
}

Stream::Stream(Stream&& other) noexcept : handle_(other.handle_) {
    other.handle_ = nullptr;
}

Stream& Stream::operator=(Stream&& other) noexcept {
    if (this != &other) {
        handle_ = other.handle_;
        other.handle_ = nullptr;
    }
    return *this;
}

core::Status Stream::synchronize() {
#ifdef IMGPROC_WITH_CUDA
    if (handle_ != nullptr &&
        cudaStreamSynchronize(static_cast<cudaStream_t>(handle_)) != cudaSuccess) {
        return core::CudaError("cudaStreamSynchronize failed");
    }
    return core::Status::Ok();
#else
    return core::Unsupported("built without CUDA support");
#endif
}

}  // namespace imgproc::cuda
