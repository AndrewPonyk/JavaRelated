// tests/unit/test_gaussian_blur.cpp
#include "imgproc/filters/gaussian_blur.hpp"

#include <gtest/gtest.h>

#include "imgproc/core/image.hpp"

namespace {

using imgproc::core::Image;
using imgproc::core::PixelFormat;
using imgproc::core::StatusCode;
using imgproc::filters::GaussianBlurParams;
using imgproc::filters::gaussianBlur;

TEST(GaussianBlurTest, RejectsEmptyImage) {
    Image src;
    Image dst;
    EXPECT_EQ(gaussianBlur(src, dst, GaussianBlurParams{}).code(),
              StatusCode::kInvalidArgument);
}

TEST(GaussianBlurTest, RejectsEvenKernelSize) {
    Image src(8, 8, PixelFormat::kGray8);
    Image dst;
    GaussianBlurParams params;
    params.kernel_size = 4;
    EXPECT_EQ(gaussianBlur(src, dst, params).code(), StatusCode::kInvalidArgument);
}

TEST(GaussianBlurTest, ConstantImageStaysConstant) {
    Image src(16, 16, PixelFormat::kGray8);
    src.fill(100);
    Image dst;
    GaussianBlurParams params;
    params.kernel_size = 5;
    params.sigma = 1.5F;
    ASSERT_TRUE(gaussianBlur(src, dst, params).ok());
    ASSERT_EQ(dst.byte_size(), src.byte_size());
    for (int y = 0; y < dst.height(); ++y) {
        for (int x = 0; x < dst.width(); ++x) {
            EXPECT_EQ(dst.at(y, x, 0), 100);  // normalized kernel preserves a flat field
        }
    }
}

TEST(GaussianBlurTest, PreservesDimensionsAndFormat) {
    Image src(12, 20, PixelFormat::kBgr8);
    src.fill(50);
    Image dst;
    GaussianBlurParams params;
    params.kernel_size = 3;
    ASSERT_TRUE(gaussianBlur(src, dst, params).ok());
    EXPECT_EQ(dst.width(), 20);
    EXPECT_EQ(dst.height(), 12);
    EXPECT_EQ(dst.channels(), 3);
}

TEST(GaussianBlurTest, SmoothsAnImpulse) {
    Image src(9, 9, PixelFormat::kGray8);
    src.fill(0);
    src.at(4, 4, 0) = 255;  // single bright pixel
    Image dst;
    GaussianBlurParams params;
    params.kernel_size = 5;
    params.sigma = 1.0F;
    ASSERT_TRUE(gaussianBlur(src, dst, params).ok());
    // Energy spreads: center drops below 255, neighbours rise above 0.
    EXPECT_LT(dst.at(4, 4, 0), 255);
    EXPECT_GT(dst.at(4, 5, 0), 0);
}

}  // namespace
