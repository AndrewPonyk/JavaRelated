// src/tracking/tracker.cpp
// SORT-style multi-object tracker: constant-velocity motion prediction +
// greedy IoU association + track lifecycle management.
#include "imgproc/tracking/tracker.hpp"

#include <algorithm>
#include <tuple>
#include <vector>

#include "imgproc/core/geometry.hpp"

namespace imgproc::tracking {

namespace {

struct KTrack {
    int id{0};
    core::BBox box{};       // current (corrected) estimate
    float vx{0.0F};         // center-x velocity (px/frame)
    float vy{0.0F};         // center-y velocity (px/frame)
    int class_id{-1};
    int age{0};
    int hits{0};
    int time_since_update{0};

    [[nodiscard]] float cx() const noexcept { return box.x + box.width * 0.5F; }
    [[nodiscard]] float cy() const noexcept { return box.y + box.height * 0.5F; }

    void predict() {
        box.x += vx;
        box.y += vy;
        ++age;
        ++time_since_update;
    }
};

}  // namespace

struct Tracker::Impl {
    TrackerConfig config;
    std::vector<KTrack> tracks;
    int next_id{1};
    long frame_count{0};
};

Tracker::Tracker(TrackerConfig config) : impl_(std::make_unique<Impl>()) {
    impl_->config = config;
}
Tracker::~Tracker() = default;
Tracker::Tracker(Tracker&&) noexcept = default;
Tracker& Tracker::operator=(Tracker&&) noexcept = default;

core::Status Tracker::update(const core::Detections& detections,
                             core::Tracks& out_tracks) {
    auto& cfg = impl_->config;
    auto& tracks = impl_->tracks;
    ++impl_->frame_count;

    // 1. Predict every existing track forward one frame.
    for (auto& t : tracks) {
        t.predict();
    }

    // 2. Build candidate matches (IoU >= threshold) and greedily assign by
    //    descending IoU so each track and detection is used at most once.
    std::vector<std::tuple<float, std::size_t, std::size_t>> candidates;  // (iou, track, det)
    for (std::size_t ti = 0; ti < tracks.size(); ++ti) {
        for (std::size_t di = 0; di < detections.size(); ++di) {
            const float score = core::iou(tracks[ti].box, detections[di].box);
            if (score >= cfg.iou_threshold) {
                candidates.emplace_back(score, ti, di);
            }
        }
    }
    std::sort(candidates.begin(), candidates.end(),
              [](const auto& a, const auto& b) { return std::get<0>(a) > std::get<0>(b); });

    std::vector<bool> track_used(tracks.size(), false);
    std::vector<bool> det_used(detections.size(), false);

    for (const auto& [score, ti, di] : candidates) {
        if (track_used[ti] || det_used[di]) {
            continue;
        }
        track_used[ti] = true;
        det_used[di] = true;

        KTrack& t = tracks[ti];
        const core::BBox& m = detections[di].box;
        const float meas_cx = m.x + m.width * 0.5F;
        const float meas_cy = m.y + m.height * 0.5F;
        // Velocity from the previous estimate's center to the measurement.
        t.vx = meas_cx - t.cx();
        t.vy = meas_cy - t.cy();
        t.box = m;                 // correct the estimate with the measurement
        t.class_id = detections[di].class_id;
        t.time_since_update = 0;
        ++t.hits;
    }

    // 3. Spawn new tentative tracks for unmatched detections.
    for (std::size_t di = 0; di < detections.size(); ++di) {
        if (det_used[di]) {
            continue;
        }
        KTrack t;
        t.id = impl_->next_id++;
        t.box = detections[di].box;
        t.class_id = detections[di].class_id;
        t.age = 1;
        t.hits = 1;
        t.time_since_update = 0;
        tracks.push_back(t);
    }

    // 4. Remove dead tracks (unmatched for too long).
    tracks.erase(std::remove_if(tracks.begin(), tracks.end(),
                                [&](const KTrack& t) {
                                    return t.time_since_update > cfg.max_age;
                                }),
                 tracks.end());

    // 5. Emit confirmed tracks updated this frame. During the initial warmup
    //    (first `min_hits` frames) emit fresh tracks too, matching SORT.
    out_tracks.clear();
    for (const auto& t : tracks) {
        const bool confirmed = t.hits >= cfg.min_hits ||
                               impl_->frame_count <= cfg.min_hits;
        if (t.time_since_update == 0 && confirmed) {
            core::Track ot;
            ot.track_id = t.id;
            ot.box = t.box;
            ot.class_id = t.class_id;
            ot.velocity_x = t.vx;
            ot.velocity_y = t.vy;
            ot.age = t.age;
            ot.time_since_update = t.time_since_update;
            out_tracks.push_back(ot);
        }
    }
    return core::Status::Ok();
}

void Tracker::reset() {
    impl_->tracks.clear();
    impl_->next_id = 1;
    impl_->frame_count = 0;
}

}  // namespace imgproc::tracking
