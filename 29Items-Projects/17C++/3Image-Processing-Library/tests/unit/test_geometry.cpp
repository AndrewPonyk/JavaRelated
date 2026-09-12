// tests/unit/test_geometry.cpp
#include "imgproc/core/geometry.hpp"

#include <gtest/gtest.h>

namespace {

using imgproc::core::BBox;
using imgproc::core::Detection;
using imgproc::core::Detections;
using imgproc::core::iou;
using imgproc::core::nonMaxSuppression;

TEST(GeometryTest, IouIdentical) {
    BBox a{0, 0, 10, 10};
    EXPECT_FLOAT_EQ(iou(a, a), 1.0F);
}

TEST(GeometryTest, IouDisjoint) {
    BBox a{0, 0, 10, 10};
    BBox b{100, 100, 10, 10};
    EXPECT_FLOAT_EQ(iou(a, b), 0.0F);
}

TEST(GeometryTest, IouHalfHorizontalOverlap) {
    BBox a{0, 0, 10, 10};
    BBox b{5, 0, 10, 10};
    // inter = 5*10 = 50, union = 100 + 100 - 50 = 150 -> 1/3
    EXPECT_NEAR(iou(a, b), 1.0F / 3.0F, 1e-5);
}

TEST(NmsTest, SuppressesOverlappingSameClass) {
    Detections d;
    d.push_back(Detection{BBox{0, 0, 10, 10}, 0, 0.9F, "a"});
    d.push_back(Detection{BBox{1, 1, 10, 10}, 0, 0.8F, "a"});  // heavy overlap, lower score
    const auto kept = nonMaxSuppression(d, 0.5F);
    ASSERT_EQ(kept.size(), 1u);
    EXPECT_FLOAT_EQ(kept[0].score, 0.9F);
}

TEST(NmsTest, KeepsDifferentClasses) {
    Detections d;
    d.push_back(Detection{BBox{0, 0, 10, 10}, 0, 0.9F, "a"});
    d.push_back(Detection{BBox{1, 1, 10, 10}, 1, 0.8F, "b"});  // overlap but different class
    const auto kept = nonMaxSuppression(d, 0.5F);
    EXPECT_EQ(kept.size(), 2u);
}

TEST(NmsTest, SortsByDescendingScore) {
    Detections d;
    d.push_back(Detection{BBox{0, 0, 10, 10}, 0, 0.3F, "a"});
    d.push_back(Detection{BBox{100, 100, 10, 10}, 0, 0.95F, "a"});
    const auto kept = nonMaxSuppression(d, 0.5F);
    ASSERT_EQ(kept.size(), 2u);
    EXPECT_GT(kept[0].score, kept[1].score);
}

}  // namespace
