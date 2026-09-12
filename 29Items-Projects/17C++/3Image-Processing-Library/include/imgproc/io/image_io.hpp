// imgproc/io/image_io.hpp
// Image/video decode + encode helpers (OpenCV-backed) with input hardening
// (size/dimension limits to guard against decompression bombs).
#pragma once

#include <string>

#include "imgproc/core/image.hpp"
#include "imgproc/core/status.hpp"

namespace imgproc::io {

struct DecodeLimits {
    int max_width{16384};
    int max_height{16384};
    std::size_t max_bytes{256U * 1024U * 1024U};  // 256 MiB
};

/// Decode an image file from disk into a host Image. Enforces `limits`.
[[nodiscard]] core::Status readImage(const std::string& path,
                                     core::Image& out,
                                     const DecodeLimits& limits = {});

/// Encode and write a host Image to disk (format inferred from extension).
[[nodiscard]] core::Status writeImage(const std::string& path,
                                      const core::Image& image);

}  // namespace imgproc::io
