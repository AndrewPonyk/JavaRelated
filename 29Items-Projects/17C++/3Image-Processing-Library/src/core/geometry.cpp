// src/core/geometry.cpp
#include "imgproc/core/geometry.hpp"

#include <algorithm>
#include <numeric>

namespace imgproc::core {

float intersectionArea(const BBox& a, const BBox& b) noexcept {
    const float x1 = std::max(a.x, b.x);
    const float y1 = std::max(a.y, b.y);
    const float x2 = std::min(a.right(), b.right());
    const float y2 = std::min(a.bottom(), b.bottom());
    const float w = std::max(0.0F, x2 - x1);
    const float h = std::max(0.0F, y2 - y1);
    return w * h;
}

float iou(const BBox& a, const BBox& b) noexcept {
    const float inter = intersectionArea(a, b);
    const float uni = a.area() + b.area() - inter;
    return uni > 0.0F ? inter / uni : 0.0F;
}

Detections nonMaxSuppression(const Detections& dets, float iou_threshold) {
    // Sort indices by descending score.
    std::vector<std::size_t> order(dets.size());
    std::iota(order.begin(), order.end(), std::size_t{0});
    std::sort(order.begin(), order.end(), [&](std::size_t i, std::size_t j) {
        return dets[i].score > dets[j].score;
    });

    std::vector<bool> suppressed(dets.size(), false);
    Detections kept;
    kept.reserve(dets.size());

    for (std::size_t oi = 0; oi < order.size(); ++oi) {
        const std::size_t i = order[oi];
        if (suppressed[i]) {
            continue;
        }
        kept.push_back(dets[i]);
        for (std::size_t oj = oi + 1; oj < order.size(); ++oj) {
            const std::size_t j = order[oj];
            if (suppressed[j]) {
                continue;
            }
            if (dets[i].class_id == dets[j].class_id &&
                iou(dets[i].box, dets[j].box) > iou_threshold) {
                suppressed[j] = true;
            }
        }
    }
    return kept;
}

}  // namespace imgproc::core
