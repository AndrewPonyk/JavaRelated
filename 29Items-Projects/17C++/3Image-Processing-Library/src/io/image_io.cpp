// src/io/image_io.cpp
// Pure-C++ Netpbm (PPM/PGM) codec. No OpenCV dependency, so the IO path builds
// and tests anywhere. Enforces decode limits to guard against malformed or
// decompression-bomb inputs (ARCHITECTURE.md §2.5). An optional OpenCV codec
// for JPEG/PNG/etc. lives behind IMGPROC_WITH_OPENCV.
#include "imgproc/io/image_io.hpp"

#include <cctype>
#include <cstdint>
#include <fstream>
#include <string>
#include <vector>

namespace imgproc::io {

namespace {

bool isSpace(int c) { return c != EOF && std::isspace(static_cast<unsigned char>(c)) != 0; }

// Read the next whitespace-delimited token, skipping '#' comments to EOL. The
// single delimiter character that terminates the token is consumed from the
// stream (this is the whitespace separating the header from binary pixel data).
bool nextToken(std::istream& in, std::string& tok) {
    tok.clear();
    int c = in.get();
    while (in) {
        if (c == '#') {
            while (in && c != '\n') {
                c = in.get();
            }
        } else if (isSpace(c)) {
            // skip
        } else {
            break;
        }
        c = in.get();
    }
    if (!in) {
        return false;
    }
    while (in && !isSpace(c) && c != '#') {
        tok.push_back(static_cast<char>(c));
        c = in.get();
    }
    return !tok.empty();
}

bool endsWith(const std::string& s, const std::string& suf) {
    return s.size() >= suf.size() && s.compare(s.size() - suf.size(), suf.size(), suf) == 0;
}

}  // namespace

core::Status readImage(const std::string& path, core::Image& out,
                       const DecodeLimits& limits) {
    if (path.empty()) {
        return core::InvalidArgument("readImage: empty path");
    }
    std::ifstream f(path, std::ios::binary);
    if (!f) {
        return {core::StatusCode::kIoError, "readImage: cannot open " + path};
    }

    // Decompression-bomb guard: cap total file size.
    f.seekg(0, std::ios::end);
    const auto file_size = static_cast<std::size_t>(f.tellg());
    f.seekg(0, std::ios::beg);
    if (file_size > limits.max_bytes) {
        return core::InvalidArgument("readImage: file exceeds max_bytes limit");
    }

    std::string magic;
    if (!nextToken(f, magic) || (magic != "P5" && magic != "P6")) {
        return core::Unsupported("readImage: only binary PGM (P5) / PPM (P6) supported");
    }
    std::string tw, th, tmax;
    if (!nextToken(f, tw) || !nextToken(f, th) || !nextToken(f, tmax)) {
        return {core::StatusCode::kIoError, "readImage: malformed Netpbm header"};
    }

    int width = 0, height = 0, maxval = 0;
    try {
        width = std::stoi(tw);
        height = std::stoi(th);
        maxval = std::stoi(tmax);
    } catch (...) {
        return {core::StatusCode::kIoError, "readImage: invalid header numbers"};
    }
    if (width <= 0 || height <= 0) {
        return core::InvalidArgument("readImage: non-positive dimensions");
    }
    if (width > limits.max_width || height > limits.max_height) {
        return core::InvalidArgument("readImage: dimensions exceed decode limits");
    }
    if (maxval != 255) {
        return core::Unsupported("readImage: only 8-bit (maxval 255) supported");
    }
    // NOTE: nextToken() already consumed the single whitespace separating the
    // header from the binary pixel data, so we read pixels directly here.

    const int channels = (magic == "P6") ? 3 : 1;
    const auto pixel_bytes =
        static_cast<std::size_t>(width) * static_cast<std::size_t>(height) *
        static_cast<std::size_t>(channels);

    core::Image img(height, width,
                    channels == 3 ? core::PixelFormat::kRgb8 : core::PixelFormat::kGray8);
    f.read(reinterpret_cast<char*>(img.data()), static_cast<std::streamsize>(pixel_bytes));
    if (static_cast<std::size_t>(f.gcount()) != pixel_bytes) {
        return {core::StatusCode::kIoError, "readImage: truncated pixel data"};
    }
    out = std::move(img);
    return core::Status::Ok();
}

core::Status writeImage(const std::string& path, const core::Image& image) {
    if (path.empty()) {
        return core::InvalidArgument("writeImage: empty path");
    }
    if (image.empty()) {
        return core::InvalidArgument("writeImage: empty image");
    }

    // Choose the Netpbm flavour from the channel count / extension.
    core::Image to_write;
    const bool want_gray = endsWith(path, ".pgm");
    const core::PixelFormat target =
        want_gray ? core::PixelFormat::kGray8 : core::PixelFormat::kRgb8;

    if (image.format() == target) {
        to_write = image.clone();
    } else if (auto st = image.convertTo(target, to_write); !st.ok()) {
        return st;
    }

    std::ofstream f(path, std::ios::binary);
    if (!f) {
        return {core::StatusCode::kIoError, "writeImage: cannot open " + path};
    }
    const char* magic = (target == core::PixelFormat::kGray8) ? "P5" : "P6";
    f << magic << "\n" << to_write.width() << " " << to_write.height() << "\n255\n";
    f.write(reinterpret_cast<const char*>(to_write.data()),
            static_cast<std::streamsize>(to_write.byte_size()));
    if (!f) {
        return {core::StatusCode::kIoError, "writeImage: write failed"};
    }
    return core::Status::Ok();
}

}  // namespace imgproc::io
