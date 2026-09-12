// tests/unit/test_tracker.cpp
#include "imgproc/tracking/tracker.hpp"

#include <gtest/gtest.h>

#include "imgproc/core/types.hpp"

namespace {

using imgproc::core::BBox;
using imgproc::core::Detection;
using imgproc::core::Detections;
using imgproc::core::Tracks;
using imgproc::tracking::Tracker;
using imgproc::tracking::TrackerConfig;

Detections one(const BBox& b, int cls = 0) {
    Detections d;
    d.push_back(Detection{b, cls, 0.9F, "object"});
    return d;
}

TEST(TrackerTest, MaintainsIdAcrossFrames) {
    Tracker tracker;
    Tracks t;
    int id = -1;
    for (int frame = 0; frame < 4; ++frame) {
        // Object drifts slightly each frame; IoU stays high.
        const auto dets = one(BBox{10.0F + static_cast<float>(frame), 10.0F, 20.0F, 20.0F});
        ASSERT_TRUE(tracker.update(dets, t).ok());
        ASSERT_EQ(t.size(), 1u) << "frame " << frame;
        if (id < 0) {
            id = t[0].track_id;
        } else {
            EXPECT_EQ(t[0].track_id, id) << "frame " << frame;
        }
    }
    EXPECT_GT(id, 0);
}

TEST(TrackerTest, AssignsDistinctIdsToSeparateObjects) {
    Tracker tracker(TrackerConfig{0.3F, 30, 1});  // min_hits=1 so both report immediately
    Detections dets;
    dets.push_back(Detection{BBox{0, 0, 10, 10}, 0, 0.9F, "object"});
    dets.push_back(Detection{BBox{100, 100, 10, 10}, 0, 0.9F, "object"});
    Tracks t;
    ASSERT_TRUE(tracker.update(dets, t).ok());
    ASSERT_EQ(t.size(), 2u);
    EXPECT_NE(t[0].track_id, t[1].track_id);
}

TEST(TrackerTest, DropsTrackAfterMaxAge) {
    TrackerConfig cfg;
    cfg.iou_threshold = 0.3F;
    cfg.max_age = 2;
    cfg.min_hits = 1;
    Tracker tracker(cfg);

    Tracks t;
    ASSERT_TRUE(tracker.update(one(BBox{10, 10, 20, 20}), t).ok());
    EXPECT_EQ(t.size(), 1u);

    // Feed empty frames; after max_age the track must disappear from output.
    Detections empty;
    for (int i = 0; i < 5; ++i) {
        ASSERT_TRUE(tracker.update(empty, t).ok());
    }
    EXPECT_TRUE(t.empty());
}

TEST(TrackerTest, ResetClearsState) {
    Tracker tracker(TrackerConfig{0.3F, 30, 1});
    Tracks t;
    ASSERT_TRUE(tracker.update(one(BBox{5, 5, 10, 10}), t).ok());
    const int first_id = t[0].track_id;
    tracker.reset();
    ASSERT_TRUE(tracker.update(one(BBox{5, 5, 10, 10}), t).ok());
    EXPECT_EQ(t[0].track_id, first_id);  // ids restart from 1 after reset
}

}  // namespace
