// tests/integration/test_pipeline.cpp
// End-to-end: synthetic frames -> detect -> track across time, plus a
// decode(PPM) -> detect -> registry-logging flow exercising every layer.
#include <gtest/gtest.h>

#include <filesystem>
#include <memory>
#include <set>
#include <string>

#include "imgproc/core/image.hpp"
#include "imgproc/core/types.hpp"
#include "imgproc/detection/object_detector.hpp"
#include "imgproc/io/image_io.hpp"
#include "imgproc/registry/model_registry.hpp"
#include "imgproc/tracking/tracker.hpp"

namespace {

namespace fs = std::filesystem;
using namespace imgproc;

core::Image frameWithBlob(int side, int cx, int cy, int half) {
    core::Image img(side, side, core::PixelFormat::kGray8);
    img.fill(0);
    for (int y = cy - half; y <= cy + half; ++y) {
        for (int x = cx - half; x <= cx + half; ++x) {
            if (y >= 0 && y < side && x >= 0 && x < side) {
                img.at(y, x, 0) = 255;
            }
        }
    }
    return img;
}

TEST(PipelineIntegrationTest, DetectAndTrackAcrossFrames) {
    detection::DetectorConfig cfg;
    cfg.denoise = false;
    std::unique_ptr<detection::ObjectDetector> detector;
    ASSERT_TRUE(detection::ObjectDetector::create(cfg, detector).ok());

    tracking::Tracker tracker;
    std::set<int> ids_seen;
    int frames_with_track = 0;

    // A blob translates left-to-right over 6 frames.
    for (int f = 0; f < 6; ++f) {
        core::Image img = frameWithBlob(64, 10 + f * 3, 32, 5);
        core::Detections dets;
        ASSERT_TRUE(detector->detect(img, dets).ok());
        ASSERT_EQ(dets.size(), 1u) << "frame " << f;

        core::Tracks tracks;
        ASSERT_TRUE(tracker.update(dets, tracks).ok());
        if (!tracks.empty()) {
            ++frames_with_track;
            ids_seen.insert(tracks[0].track_id);
        }
    }

    // The same physical object should keep ONE id for most of the sequence.
    EXPECT_GE(frames_with_track, 4);
    EXPECT_EQ(ids_seen.size(), 1u);
}

TEST(PipelineIntegrationTest, DecodeDetectAndLogToRegistry) {
    // 1. Write a frame to disk and decode it back (IO layer).
    core::Image img = frameWithBlob(48, 24, 24, 6);
    const std::string path =
        (fs::temp_directory_path() / "imgproc_pipeline_frame.pgm").string();
    ASSERT_TRUE(io::writeImage(path, img).ok());

    core::Image decoded;
    ASSERT_TRUE(io::readImage(path, decoded).ok());

    // 2. Detect.
    detection::DetectorConfig cfg;
    cfg.denoise = false;
    std::unique_ptr<detection::ObjectDetector> detector;
    ASSERT_TRUE(detection::ObjectDetector::create(cfg, detector).ok());
    core::Detections dets;
    ASSERT_TRUE(detector->detect(decoded, dets).ok());
    ASSERT_GE(dets.size(), 1u);

    // 3. Persist provenance to the registry (DB layer).
    std::unique_ptr<registry::ModelRegistry> reg;
    ASSERT_TRUE(registry::ModelRegistry::open(":memory:", reg).ok());
    registry::ModelRecord model;
    model.name = "classical-blob";
    model.version = "1.0.0";
    model.backend = "reference";
    model.artifact_path = "(builtin)";
    model.sha256 = "";
    model.input_shape = "[]";
    std::int64_t model_id = 0;
    ASSERT_TRUE(reg->createModel(model, model_id).ok());

    registry::DetectionRunRecord run;
    run.model_id = model_id;
    run.source_uri = path;
    run.device = "cpu";
    run.num_detections = static_cast<int>(dets.size());
    run.latency_ms = 1.0;
    std::int64_t run_id = 0;
    ASSERT_TRUE(reg->createRun(run, run_id).ok());

    std::vector<registry::DetectionRunRecord> runs;
    ASSERT_TRUE(reg->listRuns(model_id, runs).ok());
    ASSERT_EQ(runs.size(), 1u);
    EXPECT_EQ(runs[0].num_detections, static_cast<int>(dets.size()));

    fs::remove(path);
}

}  // namespace
