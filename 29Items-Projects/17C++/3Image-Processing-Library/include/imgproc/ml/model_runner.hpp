// imgproc/ml/model_runner.hpp
// Abstraction over an inference backend. The default `kReference` backend is a
// pure-C++ deterministic runner that needs no external model file, so the ML
// path is fully functional and testable. ONNXRuntime / OpenCV-DNN / TensorRT
// are optional accelerated backends compiled in behind feature flags.
#pragma once

#include <memory>
#include <string>
#include <vector>

#include "imgproc/core/status.hpp"
#include "imgproc/core/tensor.hpp"
#include "imgproc/core/types.hpp"

namespace imgproc::ml {

enum class BackendKind { kReference, kOnnxRuntime, kOpenCvDnn, kTensorRt };

struct ModelSpec {
    std::string artifact_path;          ///< file under IMGPROC_MODEL_DIR (empty => reference)
    std::string sha256;                 ///< if set, verified before load
    std::string model_dir;              ///< allow-list root; artifact must live here
    std::vector<int> input_shape{1, 1, 64, 64};
    BackendKind backend{BackendKind::kReference};
    core::Device device{core::Device::kCpu};

    // Reference-backend parameters (a deterministic y = x * scale + bias map).
    float ref_scale{1.0F};
    float ref_bias{0.0F};
};

/// Runs inference for a single model. Construct via `create`.
class ModelRunner {
public:
    virtual ~ModelRunner() = default;

    /// Factory: validates the artifact (allow-list + checksum) and builds the
    /// requested backend.
    static core::Status create(const ModelSpec& spec,
                               std::unique_ptr<ModelRunner>& out);

    /// Run warmup iterations to trigger lazy allocations / JIT.
    [[nodiscard]] virtual core::Status warmup(int iterations = 1) = 0;

    /// Forward pass: `input` (NCHW) -> `output`.
    [[nodiscard]] virtual core::Status infer(const core::Tensor& input,
                                             core::Tensor& output) = 0;

    /// Backend in use.
    [[nodiscard]] virtual BackendKind kind() const = 0;
};

}  // namespace imgproc::ml
