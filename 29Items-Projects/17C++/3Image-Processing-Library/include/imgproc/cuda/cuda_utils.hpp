// imgproc/cuda/cuda_utils.hpp
// Thin, header-light CUDA helpers. The public surface deliberately avoids
// including <cuda_runtime.h> so non-CUDA consumers stay clean; the heavy
// lifting lives in the .cu/.cpp translation units.
#pragma once

#include <string>

#include "imgproc/core/status.hpp"

namespace imgproc::cuda {

/// True if the build was compiled with CUDA support AND a device is present.
[[nodiscard]] bool isAvailable() noexcept;

/// Number of visible CUDA devices (0 when unavailable).
[[nodiscard]] int deviceCount() noexcept;

/// Human-readable description of device `index` (name, SM count, memory).
[[nodiscard]] core::Status deviceInfo(int index, std::string& out_description);

/// RAII wrapper around a cudaStream_t. Created/destroyed in the .cu file.
class Stream {
public:
    Stream();
    ~Stream();
    Stream(Stream&&) noexcept;
    Stream& operator=(Stream&&) noexcept;
    Stream(const Stream&) = delete;
    Stream& operator=(const Stream&) = delete;

    /// Block until all work submitted to this stream completes.
    [[nodiscard]] core::Status synchronize();

    /// Opaque handle (cudaStream_t) for use inside .cu translation units.
    [[nodiscard]] void* handle() const noexcept { return handle_; }

private:
    void* handle_{nullptr};
};

}  // namespace imgproc::cuda

// Error-checking macro used only inside .cu/.cpp translation units (where the
// CUDA runtime header is available). On a failing cudaError_t it returns a
// core::CudaError Status from the enclosing (Status-returning) function:
//
//   IMGPROC_CUDA_CHECK(cudaMemcpyAsync(...));
//
#define IMGPROC_CUDA_CHECK(expr)                                                \
    do {                                                                       \
        const cudaError_t imgproc_cuda_err_ = (expr);                          \
        if (imgproc_cuda_err_ != cudaSuccess) {                                \
            return ::imgproc::core::CudaError(                                 \
                std::string("CUDA error: ") +                                 \
                cudaGetErrorString(imgproc_cuda_err_));                        \
        }                                                                      \
    } while (0)
