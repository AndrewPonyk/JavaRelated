// src/core/image.cpp
#include "imgproc/core/image.hpp"

#include <algorithm>
#include <cassert>
#include <cstring>

namespace imgproc::core {

namespace {

int channelsFor(PixelFormat fmt) noexcept {
    switch (fmt) {
        case PixelFormat::kGray8:  return 1;
        case PixelFormat::kBgr8:
        case PixelFormat::kRgb8:
        case PixelFormat::kRgbf32: return 3;
    }
    return 0;
}

bool isColor(PixelFormat f) noexcept {
    return f == PixelFormat::kBgr8 || f == PixelFormat::kRgb8;
}

// Rec.601 luma weights for color -> gray.
std::uint8_t toGray(std::uint8_t r, std::uint8_t g, std::uint8_t b) noexcept {
    const float y = 0.299F * static_cast<float>(r) + 0.587F * static_cast<float>(g) +
                    0.114F * static_cast<float>(b);
    return static_cast<std::uint8_t>(std::clamp(y, 0.0F, 255.0F) + 0.5F);
}

}  // namespace

Image::Image(int height, int width, PixelFormat format)
    : height_(std::max(0, height)), width_(std::max(0, width)), format_(format) {
    const std::size_t bytes_per_channel = (format == PixelFormat::kRgbf32) ? 4U : 1U;
    data_.assign(static_cast<std::size_t>(height_) * static_cast<std::size_t>(width_) *
                     static_cast<std::size_t>(channelsFor(format)) * bytes_per_channel,
                 0);
}

int Image::channels() const noexcept { return channelsFor(format_); }

std::uint8_t& Image::at(int y, int x, int c) noexcept {
    assert(y >= 0 && y < height_ && x >= 0 && x < width_ && c >= 0 && c < channels());
    const std::size_t idx = (static_cast<std::size_t>(y) * static_cast<std::size_t>(width_) +
                             static_cast<std::size_t>(x)) *
                                static_cast<std::size_t>(channels()) +
                            static_cast<std::size_t>(c);
    return data_[idx];
}

const std::uint8_t& Image::at(int y, int x, int c) const noexcept {
    assert(y >= 0 && y < height_ && x >= 0 && x < width_ && c >= 0 && c < channels());
    const std::size_t idx = (static_cast<std::size_t>(y) * static_cast<std::size_t>(width_) +
                             static_cast<std::size_t>(x)) *
                                static_cast<std::size_t>(channels()) +
                            static_cast<std::size_t>(c);
    return data_[idx];
}

void Image::fill(std::uint8_t value) noexcept {
    std::fill(data_.begin(), data_.end(), value);
}

Status Image::convertTo(PixelFormat target, Image& out) const {
    if (empty()) {
        return InvalidArgument("convertTo: source image is empty");
    }
    if (format_ == PixelFormat::kRgbf32 || target == PixelFormat::kRgbf32) {
        return Unsupported("convertTo: float32 conversion not supported by Image");
    }
    if (target == format_) {
        out = *this;
        return Status::Ok();
    }

    Image dst(height_, width_, target);
    const int n = height_ * width_;

    // color -> gray
    if (isColor(format_) && target == PixelFormat::kGray8) {
        const bool bgr = (format_ == PixelFormat::kBgr8);
        for (int i = 0; i < n; ++i) {
            const std::uint8_t c0 = data_[static_cast<std::size_t>(i) * 3 + 0];
            const std::uint8_t c1 = data_[static_cast<std::size_t>(i) * 3 + 1];
            const std::uint8_t c2 = data_[static_cast<std::size_t>(i) * 3 + 2];
            const std::uint8_t r = bgr ? c2 : c0;
            const std::uint8_t b = bgr ? c0 : c2;
            dst.data()[i] = toGray(r, c1, b);
        }
        out = std::move(dst);
        return Status::Ok();
    }

    // gray -> color (replicate)
    if (format_ == PixelFormat::kGray8 && isColor(target)) {
        for (int i = 0; i < n; ++i) {
            const std::uint8_t g = data_[static_cast<std::size_t>(i)];
            dst.data()[static_cast<std::size_t>(i) * 3 + 0] = g;
            dst.data()[static_cast<std::size_t>(i) * 3 + 1] = g;
            dst.data()[static_cast<std::size_t>(i) * 3 + 2] = g;
        }
        out = std::move(dst);
        return Status::Ok();
    }

    // BGR <-> RGB (swap channels 0 and 2)
    if (isColor(format_) && isColor(target)) {
        for (int i = 0; i < n; ++i) {
            dst.data()[static_cast<std::size_t>(i) * 3 + 0] =
                data_[static_cast<std::size_t>(i) * 3 + 2];
            dst.data()[static_cast<std::size_t>(i) * 3 + 1] =
                data_[static_cast<std::size_t>(i) * 3 + 1];
            dst.data()[static_cast<std::size_t>(i) * 3 + 2] =
                data_[static_cast<std::size_t>(i) * 3 + 0];
        }
        out = std::move(dst);
        return Status::Ok();
    }

    return Unsupported("convertTo: unsupported format pair");
}

}  // namespace imgproc::core
