#include "engine/core/jobs/JobSystem.hpp"
#include "engine/procgen/TerrainGenerator.hpp"
#include "engine/procgen/TerrainModel.hpp"

#include <gtest/gtest.h>

#include <atomic>

using namespace engine;
using namespace engine::procgen;

TEST(TerrainModel, ProducesHeightfieldOfRequestedResolution) {
    TerrainModel model;
    (void) model.load("nonexistent.onnx", "v1"); // infer() uses the procedural fallback regardless

    TerrainInput in;
    in.seed       = 42;
    in.resolution = 32;
    auto hf       = model.infer(in);
    ASSERT_TRUE(hf);
    EXPECT_EQ(hf.value().resolution, 32u);
    EXPECT_EQ(hf.value().heights.size(), 32u * 32u);
}

TEST(TerrainModel, InferenceIsDeterministicForSameInput) {
    TerrainModel model;
    (void) model.load("nonexistent.onnx", "v1"); // infer() uses the procedural fallback regardless

    TerrainInput in;
    in.seed       = 7;
    in.tileX      = 2;
    in.tileY      = -3;
    in.resolution = 16;

    auto a = model.infer(in);
    auto b = model.infer(in);
    ASSERT_TRUE(a);
    ASSERT_TRUE(b);
    EXPECT_EQ(a.value().heights, b.value().heights); // reproducible from (seed, coords)
}

TEST(TerrainModel, DifferentSeedsDiffer) {
    TerrainModel model;
    (void) model.load("nonexistent.onnx", "v1"); // infer() uses the procedural fallback regardless

    TerrainInput in;
    in.resolution = 16;
    in.seed       = 1;
    auto a        = model.infer(in);
    in.seed       = 2;
    auto b        = model.infer(in);
    ASSERT_TRUE(a);
    ASSERT_TRUE(b);
    EXPECT_NE(a.value().heights, b.value().heights);
}

TEST(TerrainModel, RejectsZeroResolution) {
    TerrainModel model;
    (void) model.load("nonexistent.onnx", "v1"); // infer() uses the procedural fallback regardless
    TerrainInput in;
    in.resolution = 0;
    auto hf       = model.infer(in);
    EXPECT_FALSE(hf);
}

TEST(TerrainGenerator, RequestTileDeliversBakedMesh) {
    jobs::JobSystem jobs;
    jobs.start(2);

    TerrainModel model;
    (void) model.load("nonexistent.onnx", "v1"); // infer() uses the procedural fallback regardless

    TerrainGenerator gen(jobs, model);
    std::atomic<int> delivered{0};
    std::atomic<int> vertsSeen{0};

    gen.requestTile(1337, 0, 0, "grassland", [&](TerrainTile&& tile) {
        vertsSeen.store(static_cast<int>(tile.vertices.size()));
        EXPECT_FALSE(tile.indices.empty());
        delivered.fetch_add(1);
    });

    jobs.waitForIdle();
    jobs.stop();

    EXPECT_EQ(delivered.load(), 1);
    EXPECT_GT(vertsSeen.load(), 0);
}
