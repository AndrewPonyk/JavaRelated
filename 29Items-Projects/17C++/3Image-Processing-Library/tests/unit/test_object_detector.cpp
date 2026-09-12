// tests/unit/test_object_detector.cpp
#include "imgproc/detection/object_detector.hpp"

#include <gtest/gtest.h>

#include "imgproc/core/image.hpp"

namespace {

using imgproc::core::Detections;
using imgproc::core::Image;
using imgproc::core::PixelFormat;
using imgproc::detection::DetectorBackend;
using imgproc::detection::DetectorConfig;
using imgproc::detection::ObjectDetector;

// Build a dark image with a bright filled square.
Image makeBlobImage(int side, int bx, int by, int bw, int bh, std::uint8_t val = 255) {
    Image img(side, side, PixelFormat::kGray8);
    img.fill(0);
    for (int y = by; y < by + bh; ++y) {
        for (int x = bx; x < bx + bw; ++x) {
            img.at(y, x, 0) = val;
        }
    }
    return img;
}

TEST(ObjectDetectorTest, CreatesClassicalBackend) {
    DetectorConfig cfg;
    std::unique_ptr<ObjectDetector> det;
    ASSERT_TRUE(ObjectDetector::create(cfg, det).ok());
    ASSERT_NE(det, nullptr);
}

TEST(ObjectDetectorTest, DetectsSingleBrightBlob) {
    Image img = makeBlobImage(40, 12, 14, 8, 8);
    DetectorConfig cfg;
    cfg.denoise = false;  // keep the square crisp for an exact-ish box check
    std::unique_ptr<ObjectDetector> det;
    ASSERT_TRUE(ObjectDetector::create(cfg, det).ok());

    Detections out;
    ASSERT_TRUE(det->detect(img, out).ok());
    ASSERT_EQ(out.size(), 1u);
    EXPECT_EQ(out[0].label, "object");
    // Box should cover the bright square region.
    EXPECT_NEAR(out[0].box.x, 12.0F, 1.0F);
    EXPECT_NEAR(out[0].box.y, 14.0F, 1.0F);
    EXPECT_NEAR(out[0].box.width, 8.0F, 1.0F);
    EXPECT_GT(out[0].score, 0.5F);
}

TEST(ObjectDetectorTest, DetectsTwoSeparatedBlobs) {
    Image img(48, 48, PixelFormat::kGray8);
    img.fill(0);
    for (int y = 4; y < 12; ++y)
        for (int x = 4; x < 12; ++x) img.at(y, x, 0) = 255;
    for (int y = 30; y < 40; ++y)
        for (int x = 30; x < 40; ++x) img.at(y, x, 0) = 255;

    DetectorConfig cfg;
    cfg.denoise = false;
    std::unique_ptr<ObjectDetector> det;
    ASSERT_TRUE(ObjectDetector::create(cfg, det).ok());

    Detections out;
    ASSERT_TRUE(det->detect(img, out).ok());
    EXPECT_EQ(out.size(), 2u);
}

TEST(ObjectDetectorTest, IgnoresTinyNoiseBelowMinArea) {
    Image img(20, 20, PixelFormat::kGray8);
    img.fill(0);
    img.at(10, 10, 0) = 255;  // single pixel, below min_blob_area
    DetectorConfig cfg;
    cfg.denoise = false;
    cfg.min_blob_area = 9;
    std::unique_ptr<ObjectDetector> det;
    ASSERT_TRUE(ObjectDetector::create(cfg, det).ok());

    Detections out;
    ASSERT_TRUE(det->detect(img, out).ok());
    EXPECT_TRUE(out.empty());
}

TEST(ObjectDetectorTest, ModelBackendDetectsActivation) {
    Image img = makeBlobImage(32, 10, 10, 6, 6);
    DetectorConfig cfg;
    cfg.backend = DetectorBackend::kModel;     // reference runner by default
    cfg.model.input_shape = {1, 1, 32, 32};
    cfg.score_threshold = 0.5F;
    std::unique_ptr<ObjectDetector> det;
    ASSERT_TRUE(ObjectDetector::create(cfg, det).ok());

    Detections out;
    ASSERT_TRUE(det->detect(img, out).ok());
    ASSERT_EQ(out.size(), 1u);
    EXPECT_GE(out[0].score, 0.5F);
}

TEST(ObjectDetectorTest, RejectsEmptyImage) {
    Image empty;
    DetectorConfig cfg;
    std::unique_ptr<ObjectDetector> det;
    ASSERT_TRUE(ObjectDetector::create(cfg, det).ok());
    Detections out;
    EXPECT_FALSE(det->detect(empty, out).ok());
}

}  // namespace
