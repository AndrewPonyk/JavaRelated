// imgproc/detection/object_detector.hpp
// High-level object-detection interface. The default `kClassicalBlob` backend
// is a fully working, dependency-free detector (threshold + connected
// components). A model-backed backend (wrapping ml::ModelRunner) is selectable
// for ONNX/TensorRT deployments.
#pragma once

#include <memory>
#include <string>
#include <vector>

#include "imgproc/core/image.hpp"
#include "imgproc/core/status.hpp"
#include "imgproc/core/types.hpp"
#include "imgproc/ml/model_runner.hpp"

namespace imgproc::detection {

enum class DetectorBackend { kClassicalBlob, kModel };

struct DetectorConfig {
    DetectorBackend backend{DetectorBackend::kClassicalBlob};

    // --- Shared post-processing ---
    float score_threshold{0.25F};
    float nms_iou_threshold{0.45F};
    std::vector<std::string> class_labels{"object"};

    // --- Classical-blob parameters ---
    std::uint8_t blob_threshold{180};  ///< pixels brighter than this are foreground
    int min_blob_area{9};              ///< discard components smaller than this
    bool denoise{true};                ///< apply a light Gaussian blur first

    // --- Model-backed parameters ---
    ml::ModelSpec model{};
};

/// Detects objects in a single image. Construct via `create`.
class ObjectDetector {
public:
    virtual ~ObjectDetector() = default;

    static core::Status create(const DetectorConfig& config,
                               std::unique_ptr<ObjectDetector>& out);

    /// Run detection on one frame. Boxes are in `image` pixel coordinates.
    [[nodiscard]] virtual core::Status detect(const core::Image& image,
                                              core::Detections& out) = 0;
};

}  // namespace imgproc::detection
