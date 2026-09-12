// imgproc/core/geometry.hpp
// Geometric utilities shared by detection (NMS) and tracking (association).
#pragma once

#include "imgproc/core/types.hpp"

namespace imgproc::core {

/// Intersection area of two boxes (>= 0).
[[nodiscard]] float intersectionArea(const BBox& a, const BBox& b) noexcept;

/// Intersection-over-Union in [0, 1].
[[nodiscard]] float iou(const BBox& a, const BBox& b) noexcept;

/// Greedy non-maximum suppression. Keeps the highest-scoring boxes and removes
/// lower-scoring boxes whose IoU with a kept box of the SAME class exceeds
/// `iou_threshold`. Output preserves descending-score order.
[[nodiscard]] Detections nonMaxSuppression(const Detections& dets,
                                           float iou_threshold);

}  // namespace imgproc::core
