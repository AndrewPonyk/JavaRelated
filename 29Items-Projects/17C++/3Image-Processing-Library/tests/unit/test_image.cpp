// tests/unit/test_image.cpp
#include "imgproc/core/image.hpp"

#include <gtest/gtest.h>

namespace {

using imgproc::core::Image;
using imgproc::core::PixelFormat;
using imgproc::core::StatusCode;

TEST(ImageTest, ConstructsWithCorrectGeometry) {
    Image img(480, 640, PixelFormat::kBgr8);
    EXPECT_EQ(img.height(), 480);
    EXPECT_EQ(img.width(), 640);
    EXPECT_EQ(img.channels(), 3);
    EXPECT_FALSE(img.empty());
    EXPECT_EQ(img.byte_size(), 480u * 640u * 3u);
}

TEST(ImageTest, GrayAllocatesOneChannel) {
    Image img(2, 4, PixelFormat::kGray8);
    EXPECT_EQ(img.channels(), 1);
    EXPECT_EQ(img.byte_size(), 2u * 4u);
}

TEST(ImageTest, AtReadWrite) {
    Image img(3, 3, PixelFormat::kGray8);
    img.fill(0);
    img.at(1, 2, 0) = 200;
    EXPECT_EQ(img.at(1, 2, 0), 200);
    EXPECT_EQ(img.at(0, 0, 0), 0);
}

TEST(ImageTest, ConvertBgrToGrayUsesLuma) {
    Image bgr(1, 1, PixelFormat::kBgr8);
    bgr.at(0, 0, 0) = 0;    // B
    bgr.at(0, 0, 1) = 0;    // G
    bgr.at(0, 0, 2) = 255;  // R
    Image gray;
    ASSERT_TRUE(bgr.convertTo(PixelFormat::kGray8, gray).ok());
    // 0.299 * 255 = 76.245 -> 76
    EXPECT_EQ(gray.at(0, 0, 0), 76);
}

TEST(ImageTest, ConvertBgrToRgbSwapsChannels) {
    Image bgr(1, 1, PixelFormat::kBgr8);
    bgr.at(0, 0, 0) = 10;   // B
    bgr.at(0, 0, 1) = 20;   // G
    bgr.at(0, 0, 2) = 30;   // R
    Image rgb;
    ASSERT_TRUE(bgr.convertTo(PixelFormat::kRgb8, rgb).ok());
    EXPECT_EQ(rgb.at(0, 0, 0), 30);  // R
    EXPECT_EQ(rgb.at(0, 0, 1), 20);  // G
    EXPECT_EQ(rgb.at(0, 0, 2), 10);  // B
}

TEST(ImageTest, ConvertEmptyFails) {
    Image empty;
    Image out;
    EXPECT_EQ(empty.convertTo(PixelFormat::kGray8, out).code(),
              StatusCode::kInvalidArgument);
}

}  // namespace
