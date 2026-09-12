// imgproc/core/image.hpp
// Host-side image abstraction. Owns a contiguous, row-major, interleaved 8-bit
// pixel buffer and knows its geometry and pixel format. Pure C++ (no OpenCV);
// an optional OpenCV interop layer lives behind IMGPROC_WITH_OPENCV.
#pragma once

#include <cstddef>
#include <cstdint>
#include <vector>

#include "imgproc/core/status.hpp"
#include "imgproc/core/types.hpp"

namespace imgproc::core {

/// A simple owning, row-major host image (8-bit interleaved channels).
class Image {
public:
    Image() = default;
    Image(int height, int width, PixelFormat format);

    [[nodiscard]] int height() const noexcept { return height_; }
    [[nodiscard]] int width() const noexcept { return width_; }
    [[nodiscard]] int channels() const noexcept;
    [[nodiscard]] PixelFormat format() const noexcept { return format_; }
    [[nodiscard]] bool empty() const noexcept { return data_.empty(); }
    [[nodiscard]] std::size_t row_stride() const noexcept {
        return static_cast<std::size_t>(width_) * static_cast<std::size_t>(channels());
    }

    /// Raw buffer access.
    [[nodiscard]] std::uint8_t* data() noexcept { return data_.data(); }
    [[nodiscard]] const std::uint8_t* data() const noexcept { return data_.data(); }
    [[nodiscard]] std::size_t byte_size() const noexcept { return data_.size(); }

    /// Element access for 8-bit formats: pixel (y, x), channel c. Bounds are
    /// debug-asserted (zero cost in release); callers on hot paths guarantee
    /// indices are in range.
    [[nodiscard]] std::uint8_t& at(int y, int x, int c) noexcept;
    [[nodiscard]] const std::uint8_t& at(int y, int x, int c) const noexcept;

    /// Set every byte to `value`.
    void fill(std::uint8_t value) noexcept;

    /// Deep copy.
    [[nodiscard]] Image clone() const { return *this; }

    /// Convert between pixel formats (BGR<->RGB, color<->gray).
    [[nodiscard]] Status convertTo(PixelFormat target, Image& out) const;

private:
    int height_{0};
    int width_{0};
    PixelFormat format_{PixelFormat::kBgr8};
    std::vector<std::uint8_t> data_{};
};

}  // namespace imgproc::core
