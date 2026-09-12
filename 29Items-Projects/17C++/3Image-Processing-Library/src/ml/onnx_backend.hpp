// src/ml/onnx_backend.hpp  (internal)
// Factory for the ONNX Runtime backend. Defined in onnx_runner.cpp and compiled
// only when IMGPROC_WITH_ONNX is enabled; declared here so model_runner.cpp can
// dispatch to it without pulling in any ONNX Runtime headers.
#pragma once

#include <memory>

#include "imgproc/core/status.hpp"
#include "imgproc/ml/model_runner.hpp"

namespace imgproc::ml {

core::Status makeOnnxRunner(const ModelSpec& spec, std::unique_ptr<ModelRunner>& out);

}  // namespace imgproc::ml
