// tests/unit/test_image_io.cpp
#include "imgproc/io/image_io.hpp"

#include <gtest/gtest.h>

#include <filesystem>
#include <string>

#include "imgproc/core/image.hpp"

namespace {

namespace fs = std::filesystem;
using imgproc::core::Image;
using imgproc::core::PixelFormat;
using imgproc::core::StatusCode;
using imgproc::io::DecodeLimits;
using imgproc::io::readImage;
using imgproc::io::writeImage;

std::string tmpPath(const std::string& name) {
    return (fs::temp_directory_path() / ("imgproc_test_" + name)).string();
}

TEST(ImageIoTest, PpmRoundTrip) {
    Image src(4, 5, PixelFormat::kRgb8);
    for (int y = 0; y < 4; ++y) {
        for (int x = 0; x < 5; ++x) {
            src.at(y, x, 0) = static_cast<std::uint8_t>(x * 10);
            src.at(y, x, 1) = static_cast<std::uint8_t>(y * 20);
            src.at(y, x, 2) = static_cast<std::uint8_t>(x + y);
        }
    }
    const std::string p = tmpPath("rt.ppm");
    ASSERT_TRUE(writeImage(p, src).ok());

    Image loaded;
    ASSERT_TRUE(readImage(p, loaded).ok());
    EXPECT_EQ(loaded.width(), 5);
    EXPECT_EQ(loaded.height(), 4);
    EXPECT_EQ(loaded.channels(), 3);
    EXPECT_EQ(loaded.at(3, 4, 0), 40);
    EXPECT_EQ(loaded.at(3, 4, 1), 60);
    fs::remove(p);
}

TEST(ImageIoTest, PgmRoundTrip) {
    Image src(3, 3, PixelFormat::kGray8);
    src.at(1, 1, 0) = 123;
    const std::string p = tmpPath("rt.pgm");
    ASSERT_TRUE(writeImage(p, src).ok());

    Image loaded;
    ASSERT_TRUE(readImage(p, loaded).ok());
    EXPECT_EQ(loaded.channels(), 1);
    EXPECT_EQ(loaded.at(1, 1, 0), 123);
    fs::remove(p);
}

TEST(ImageIoTest, EnforcesDimensionLimits) {
    Image src(4, 4, PixelFormat::kGray8);
    const std::string p = tmpPath("big.pgm");
    ASSERT_TRUE(writeImage(p, src).ok());

    DecodeLimits limits;
    limits.max_width = 2;  // smaller than the 4-wide image
    Image loaded;
    EXPECT_EQ(readImage(p, loaded, limits).code(), StatusCode::kInvalidArgument);
    fs::remove(p);
}

TEST(ImageIoTest, MissingFileFails) {
    Image loaded;
    EXPECT_EQ(readImage(tmpPath("does_not_exist.ppm"), loaded).code(),
              StatusCode::kIoError);
}

}  // namespace
