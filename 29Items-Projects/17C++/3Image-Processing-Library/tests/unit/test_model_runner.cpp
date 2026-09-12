// tests/unit/test_model_runner.cpp
#include "imgproc/ml/model_runner.hpp"

#include <gtest/gtest.h>

#include <filesystem>
#include <fstream>
#include <string>

#include "imgproc/core/tensor.hpp"
#include "imgproc/util/sha256.hpp"

namespace {

namespace fs = std::filesystem;
using imgproc::core::Device;
using imgproc::core::StatusCode;
using imgproc::core::Tensor;
using imgproc::ml::BackendKind;
using imgproc::ml::ModelRunner;
using imgproc::ml::ModelSpec;

// Write `content` to a unique temp file and return its path.
std::string writeTempFile(const std::string& name, const std::string& content) {
    const std::string path = (fs::temp_directory_path() / name).string();
    std::ofstream f(path, std::ios::binary);
    f << content;
    return path;
}

TEST(ModelRunnerTest, CreatesReferenceBackend) {
    ModelSpec spec;  // default backend == kReference
    std::unique_ptr<ModelRunner> runner;
    ASSERT_TRUE(ModelRunner::create(spec, runner).ok());
    ASSERT_NE(runner, nullptr);
    EXPECT_EQ(runner->kind(), BackendKind::kReference);
}

TEST(ModelRunnerTest, WarmupSucceeds) {
    ModelSpec spec;
    std::unique_ptr<ModelRunner> runner;
    ASSERT_TRUE(ModelRunner::create(spec, runner).ok());
    EXPECT_TRUE(runner->warmup(2).ok());
}

TEST(ModelRunnerTest, InferAppliesScaleAndBias) {
    ModelSpec spec;
    spec.ref_scale = 2.0F;
    spec.ref_bias = 1.0F;
    std::unique_ptr<ModelRunner> runner;
    ASSERT_TRUE(ModelRunner::create(spec, runner).ok());

    Tensor in({1, 1, 2, 2}, Device::kCpu);
    in.fill(3.0F);
    Tensor out;
    ASSERT_TRUE(runner->infer(in, out).ok());
    ASSERT_EQ(out.numel(), 4u);
    for (std::size_t i = 0; i < out.numel(); ++i) {
        EXPECT_FLOAT_EQ(out.host_data()[i], 7.0F);  // 3*2 + 1
    }
}

TEST(ModelRunnerTest, RejectsEmptyInput) {
    ModelSpec spec;
    std::unique_ptr<ModelRunner> runner;
    ASSERT_TRUE(ModelRunner::create(spec, runner).ok());
    Tensor empty;
    Tensor out;
    EXPECT_FALSE(runner->infer(empty, out).ok());
}

TEST(ModelRunnerTest, MissingArtifactForModelBackendFails) {
    ModelSpec spec;
    spec.backend = BackendKind::kOnnxRuntime;
    spec.artifact_path = "/nonexistent/model.onnx";
    std::unique_ptr<ModelRunner> runner;
    EXPECT_EQ(ModelRunner::create(spec, runner).code(), StatusCode::kNotFound);
}

// --- Security: model-artifact allow-list + checksum validation -------------

TEST(ModelRunnerTest, RejectsArtifactOutsideModelDir) {
    const auto allowed = fs::temp_directory_path() / "imgproc_allowed_dir";
    fs::create_directories(allowed);
    const std::string outside = writeTempFile("imgproc_outside_model.bin", "weights");

    ModelSpec spec;
    spec.backend = BackendKind::kOnnxRuntime;
    spec.artifact_path = outside;
    spec.model_dir = allowed.string();  // artifact is a sibling, i.e. escapes the root

    std::unique_ptr<ModelRunner> runner;
    EXPECT_EQ(ModelRunner::create(spec, runner).code(), StatusCode::kInvalidArgument);
    fs::remove(outside);
}

TEST(ModelRunnerTest, RejectsChecksumMismatch) {
    const std::string art = writeTempFile("imgproc_cksum_bad.bin", "hello");
    ModelSpec spec;
    spec.backend = BackendKind::kOnnxRuntime;
    spec.artifact_path = art;
    spec.sha256 = "deadbeefdeadbeef";  // deliberately wrong

    std::unique_ptr<ModelRunner> runner;
    EXPECT_EQ(ModelRunner::create(spec, runner).code(), StatusCode::kModelError);
    fs::remove(art);
}

TEST(ModelRunnerTest, ValidChecksumPassesValidation) {
    const std::string art = writeTempFile("imgproc_cksum_ok.bin", "hello");
    ModelSpec spec;
    spec.backend = BackendKind::kOnnxRuntime;
    spec.artifact_path = art;
    spec.sha256 = imgproc::util::Sha256::hashFile(art);  // correct checksum

    // The checksum gate is cleared (it would otherwise return kModelError with a
    // "checksum mismatch" message). What happens next depends on the build:
    std::unique_ptr<ModelRunner> runner;
    const auto code = ModelRunner::create(spec, runner).code();
#ifdef IMGPROC_WITH_ONNX
    // Gate passed; loading "hello" as an ONNX model then fails (not a real model).
    EXPECT_EQ(code, StatusCode::kModelError);
#else
    // Gate passed; the ONNX backend is simply not compiled into this build.
    EXPECT_EQ(code, StatusCode::kUnsupported);
#endif
    fs::remove(art);
}

}  // namespace
