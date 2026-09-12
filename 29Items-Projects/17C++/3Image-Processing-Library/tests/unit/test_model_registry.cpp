// tests/unit/test_model_registry.cpp
#include "imgproc/registry/model_registry.hpp"

#include <gtest/gtest.h>

#include <memory>

namespace {

using imgproc::core::StatusCode;
using imgproc::registry::DetectionRunRecord;
using imgproc::registry::LabelRecord;
using imgproc::registry::ModelRecord;
using imgproc::registry::ModelRegistry;

std::unique_ptr<ModelRegistry> openMemory() {
    std::unique_ptr<ModelRegistry> reg;
    EXPECT_TRUE(ModelRegistry::open(":memory:", reg).ok());
    return reg;
}

ModelRecord sampleModel(const std::string& name = "yolov8n",
                        const std::string& ver = "1.0.0") {
    ModelRecord m;
    m.name = name;
    m.version = ver;
    m.backend = "reference";
    m.artifact_path = "models/" + name + ".onnx";
    m.sha256 = "deadbeef";
    m.input_shape = "[1,3,640,640]";
    return m;
}

TEST(ModelRegistryTest, CreateAndGet) {
    auto reg = openMemory();
    std::int64_t id = 0;
    ASSERT_TRUE(reg->createModel(sampleModel(), id).ok());
    EXPECT_GT(id, 0);

    ModelRecord got;
    ASSERT_TRUE(reg->getModel(id, got).ok());
    EXPECT_EQ(got.name, "yolov8n");
    EXPECT_EQ(got.version, "1.0.0");
    EXPECT_EQ(got.backend, "reference");
    EXPECT_FALSE(got.created_at.empty());
}

TEST(ModelRegistryTest, FindByNameVersion) {
    auto reg = openMemory();
    std::int64_t id = 0;
    ASSERT_TRUE(reg->createModel(sampleModel(), id).ok());
    ModelRecord got;
    ASSERT_TRUE(reg->findModel("yolov8n", "1.0.0", got).ok());
    EXPECT_EQ(got.id, id);
    EXPECT_EQ(reg->findModel("nope", "9.9.9", got).code(), StatusCode::kNotFound);
}

TEST(ModelRegistryTest, RejectsDuplicateNameVersion) {
    auto reg = openMemory();
    std::int64_t id = 0;
    ASSERT_TRUE(reg->createModel(sampleModel(), id).ok());
    std::int64_t id2 = 0;
    EXPECT_EQ(reg->createModel(sampleModel(), id2).code(), StatusCode::kInvalidArgument);
}

TEST(ModelRegistryTest, ListReturnsAll) {
    auto reg = openMemory();
    std::int64_t id = 0;
    ASSERT_TRUE(reg->createModel(sampleModel("a", "1"), id).ok());
    ASSERT_TRUE(reg->createModel(sampleModel("b", "1"), id).ok());
    std::vector<ModelRecord> all;
    ASSERT_TRUE(reg->listModels(all).ok());
    EXPECT_EQ(all.size(), 2u);
}

TEST(ModelRegistryTest, UpdateChangesFields) {
    auto reg = openMemory();
    std::int64_t id = 0;
    ASSERT_TRUE(reg->createModel(sampleModel(), id).ok());
    ModelRecord m = sampleModel();
    m.id = id;
    m.backend = "onnxruntime";
    ASSERT_TRUE(reg->updateModel(m).ok());
    ModelRecord got;
    ASSERT_TRUE(reg->getModel(id, got).ok());
    EXPECT_EQ(got.backend, "onnxruntime");
}

TEST(ModelRegistryTest, DeleteRemovesModel) {
    auto reg = openMemory();
    std::int64_t id = 0;
    ASSERT_TRUE(reg->createModel(sampleModel(), id).ok());
    ASSERT_TRUE(reg->deleteModel(id).ok());
    ModelRecord got;
    EXPECT_EQ(reg->getModel(id, got).code(), StatusCode::kNotFound);
    EXPECT_EQ(reg->deleteModel(id).code(), StatusCode::kNotFound);
}

TEST(ModelRegistryTest, LabelsReplaceAndRead) {
    auto reg = openMemory();
    std::int64_t id = 0;
    ASSERT_TRUE(reg->createModel(sampleModel(), id).ok());

    std::vector<LabelRecord> labels;
    labels.push_back(LabelRecord{0, id, 0, "person"});
    labels.push_back(LabelRecord{0, id, 1, "car"});
    ASSERT_TRUE(reg->setLabels(id, labels).ok());

    std::vector<LabelRecord> got;
    ASSERT_TRUE(reg->getLabels(id, got).ok());
    ASSERT_EQ(got.size(), 2u);
    EXPECT_EQ(got[0].label, "person");
    EXPECT_EQ(got[1].label, "car");

    // Replacing overwrites the previous set.
    std::vector<LabelRecord> repl{LabelRecord{0, id, 0, "dog"}};
    ASSERT_TRUE(reg->setLabels(id, repl).ok());
    ASSERT_TRUE(reg->getLabels(id, got).ok());
    ASSERT_EQ(got.size(), 1u);
    EXPECT_EQ(got[0].label, "dog");
}

TEST(ModelRegistryTest, RunsCreateAndList) {
    auto reg = openMemory();
    std::int64_t id = 0;
    ASSERT_TRUE(reg->createModel(sampleModel(), id).ok());

    DetectionRunRecord run;
    run.model_id = id;
    run.source_uri = "frame.ppm";
    run.device = "cpu";
    run.num_detections = 3;
    run.latency_ms = 4.2;
    std::int64_t run_id = 0;
    ASSERT_TRUE(reg->createRun(run, run_id).ok());
    EXPECT_GT(run_id, 0);

    DetectionRunRecord got;
    ASSERT_TRUE(reg->getRun(run_id, got).ok());
    EXPECT_EQ(got.num_detections, 3);
    EXPECT_DOUBLE_EQ(got.latency_ms, 4.2);

    std::vector<DetectionRunRecord> runs;
    ASSERT_TRUE(reg->listRuns(id, runs).ok());
    EXPECT_EQ(runs.size(), 1u);
}

TEST(ModelRegistryTest, RunsPaginate) {
    auto reg = openMemory();
    std::int64_t id = 0;
    ASSERT_TRUE(reg->createModel(sampleModel(), id).ok());
    for (int i = 0; i < 5; ++i) {
        DetectionRunRecord run;
        run.model_id = id;
        run.device = "cpu";
        run.num_detections = i;
        std::int64_t rid = 0;
        ASSERT_TRUE(reg->createRun(run, rid).ok());
    }
    std::vector<DetectionRunRecord> page;
    ASSERT_TRUE(reg->listRuns(id, page, /*limit=*/2, /*offset=*/0).ok());
    ASSERT_EQ(page.size(), 2u);

    ASSERT_TRUE(reg->listRuns(id, page, /*limit=*/2, /*offset=*/4).ok());
    EXPECT_EQ(page.size(), 1u);  // only one row left after offset 4 of 5

    ASSERT_TRUE(reg->listRuns(id, page).ok());  // limit<=0 => all
    EXPECT_EQ(page.size(), 5u);
}

TEST(ModelRegistryTest, RunWithUnknownModelRejectedByForeignKey) {
    auto reg = openMemory();
    DetectionRunRecord run;
    run.model_id = 9999;  // no such model
    run.device = "cpu";
    std::int64_t run_id = 0;
    EXPECT_EQ(reg->createRun(run, run_id).code(), StatusCode::kInvalidArgument);
}

}  // namespace
