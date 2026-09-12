// tests/unit/test_onnx_runner.cpp
// Real ONNX Runtime inference test. Compiled to nothing unless the backend is
// enabled (-DIMGPROC_WITH_ONNX=ON). Uses tests/data/affine.onnx, which computes
// output = input * 2 + 1, so the result is exactly verifiable.
#ifdef IMGPROC_WITH_ONNX

#include <gtest/gtest.h>

#include <memory>
#include <string>

#include "imgproc/core/tensor.hpp"
#include "imgproc/ml/model_runner.hpp"

namespace {

using imgproc::core::Device;
using imgproc::core::StatusCode;
using imgproc::core::Tensor;
using imgproc::ml::BackendKind;
using imgproc::ml::ModelRunner;
using imgproc::ml::ModelSpec;

std::string affineModel() {
    return std::string(IMGPROC_TEST_DATA_DIR) + "/affine.onnx";
}

ModelSpec affineSpec() {
    ModelSpec spec;
    spec.backend = BackendKind::kOnnxRuntime;
    spec.artifact_path = affineModel();
    spec.input_shape = {1, 1, 4, 4};
    return spec;
}

TEST(OnnxRunnerTest, CreatesOnnxBackend) {
    std::unique_ptr<ModelRunner> runner;
    ASSERT_TRUE(ModelRunner::create(affineSpec(), runner).ok());
    ASSERT_NE(runner, nullptr);
    EXPECT_EQ(runner->kind(), BackendKind::kOnnxRuntime);
}

TEST(OnnxRunnerTest, RunsRealInference) {
    std::unique_ptr<ModelRunner> runner;
    ASSERT_TRUE(ModelRunner::create(affineSpec(), runner).ok());

    Tensor in({1, 1, 4, 4}, Device::kCpu);
    in.fill(3.0F);
    Tensor out;
    ASSERT_TRUE(runner->infer(in, out).ok());

    ASSERT_EQ(out.numel(), 16u);
    for (std::size_t i = 0; i < out.numel(); ++i) {
        EXPECT_FLOAT_EQ(out.host_data()[i], 7.0F);  // 3 * 2 + 1
    }
}

TEST(OnnxRunnerTest, WarmupSucceeds) {
    std::unique_ptr<ModelRunner> runner;
    ASSERT_TRUE(ModelRunner::create(affineSpec(), runner).ok());
    EXPECT_TRUE(runner->warmup(2).ok());
}

TEST(OnnxRunnerTest, ChecksumStillEnforcedForOnnx) {
    ModelSpec spec = affineSpec();
    spec.sha256 = "deadbeef";  // wrong -> validateArtifact must reject before load
    std::unique_ptr<ModelRunner> runner;
    EXPECT_EQ(ModelRunner::create(spec, runner).code(), StatusCode::kModelError);
}

}  // namespace

#endif  // IMGPROC_WITH_ONNX
