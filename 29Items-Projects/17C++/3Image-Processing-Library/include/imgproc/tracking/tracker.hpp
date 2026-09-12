// imgproc/tracking/tracker.hpp
// Multi-object tracker. Associates per-frame detections into persistent tracks
// (SORT-style: IoU matching + Kalman motion prediction).
#pragma once

#include <memory>

#include "imgproc/core/status.hpp"
#include "imgproc/core/types.hpp"

namespace imgproc::tracking {

struct TrackerConfig {
    float iou_threshold{0.3F};   ///< min IoU to associate a detection to a track
    int max_age{30};             ///< frames to keep a track without a match
    int min_hits{3};             ///< confirmations before a track is reported
};

/// Stateful tracker; call update() once per frame in temporal order.
class Tracker {
public:
    explicit Tracker(TrackerConfig config = {});
    ~Tracker();
    Tracker(Tracker&&) noexcept;
    Tracker& operator=(Tracker&&) noexcept;

    /// Advance the tracker by one frame with the frame's detections.
    [[nodiscard]] core::Status update(const core::Detections& detections,
                                      core::Tracks& out_tracks);

    /// Drop all state (e.g. on a scene cut).
    void reset();

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

}  // namespace imgproc::tracking
