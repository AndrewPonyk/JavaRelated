// imgproc/core/types.hpp
// Fundamental value types shared across the library.
#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace imgproc::core {

/// Compute device selector. CPU is always available; CUDA requires a device.
enum class Device : std::uint8_t { kCpu, kCuda };

/// Pixel memory layout for an Image / Tensor.
enum class PixelFormat : std::uint8_t { kGray8, kBgr8, kRgb8, kRgbf32 };

/// Axis-aligned bounding box in pixel coordinates (top-left origin).
struct BBox {
    float x{0.F};       ///< left
    float y{0.F};       ///< top
    float width{0.F};
    float height{0.F};

    [[nodiscard]] float area() const noexcept { return width * height; }
    [[nodiscard]] float right() const noexcept { return x + width; }
    [[nodiscard]] float bottom() const noexcept { return y + height; }
};

/// A single object detection produced by an ObjectDetector.
struct Detection {
    BBox box{};
    int class_id{-1};
    float score{0.F};
    std::string label{};
};

/// A tracked object: a detection with a persistent identity over time.
struct Track {
    int track_id{-1};
    BBox box{};
    int class_id{-1};
    float velocity_x{0.F};
    float velocity_y{0.F};
    int age{0};         ///< frames since first seen
    int time_since_update{0};
};

using Detections = std::vector<Detection>;
using Tracks = std::vector<Track>;

}  // namespace imgproc::core
