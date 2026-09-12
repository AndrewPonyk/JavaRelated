// src/ml/model_runner.cpp
#include "imgproc/ml/model_runner.hpp"

#include <filesystem>
#include <utility>

#include "imgproc/util/sha256.hpp"

#ifdef IMGPROC_WITH_ONNX
#include "onnx_backend.hpp"
#endif

namespace imgproc::ml {

namespace {

/// Deterministic, dependency-free runner: output = input * scale + bias.
/// Stands in for a real neural network so the ML pipeline is fully exercisable.
class ReferenceRunner final : public ModelRunner {
public:
    explicit ReferenceRunner(ModelSpec spec) : spec_(std::move(spec)) {}

    core::Status warmup(int iterations) override {
        if (iterations < 0) {
            return core::InvalidArgument("warmup: iterations must be >= 0");
        }
        core::Tensor dummy(spec_.input_shape, core::Device::kCpu);
        core::Tensor out;
        for (int i = 0; i < iterations; ++i) {
            if (auto st = infer(dummy, out); !st.ok()) {
                return st;
            }
        }
        return core::Status::Ok();
    }

    core::Status infer(const core::Tensor& input, core::Tensor& output) override {
        if (input.numel() == 0) {
            return core::InvalidArgument("infer: empty input tensor");
        }
        core::Tensor out(input.shape(), core::Device::kCpu);
        const float* in = input.host_data();
        float* dst = out.host_data();
        const std::size_t n = input.numel();
        for (std::size_t i = 0; i < n; ++i) {
            dst[i] = in[i] * spec_.ref_scale + spec_.ref_bias;
        }
        output = std::move(out);
        return core::Status::Ok();
    }

    [[nodiscard]] BackendKind kind() const override { return BackendKind::kReference; }

private:
    ModelSpec spec_;
};

/// Validate an artifact path: must exist, sit under the allow-listed model_dir
/// (if provided), and match the declared SHA-256 (if provided).
core::Status validateArtifact(const ModelSpec& spec) {
    namespace fs = std::filesystem;

    if (spec.backend == BackendKind::kReference) {
        return core::Status::Ok();  // reference backend uses no file
    }
    if (spec.artifact_path.empty()) {
        return core::InvalidArgument("ModelSpec.artifact_path is empty");
    }

    std::error_code ec;
    const fs::path artifact = fs::weakly_canonical(fs::path(spec.artifact_path), ec);
    if (ec || !fs::exists(artifact)) {
        return {core::StatusCode::kNotFound, "model artifact not found: " + spec.artifact_path};
    }

    if (!spec.model_dir.empty()) {
        const fs::path root = fs::weakly_canonical(fs::path(spec.model_dir), ec);
        const std::string rel = fs::relative(artifact, root, ec).generic_string();
        if (ec || rel.empty() || rel.rfind("..", 0) == 0) {
            return core::InvalidArgument(
                "model artifact escapes allow-listed IMGPROC_MODEL_DIR");
        }
    }

    if (!spec.sha256.empty()) {
        const std::string actual = util::Sha256::hashFile(artifact.string());
        if (actual != spec.sha256) {
            return {core::StatusCode::kModelError,
                    "artifact checksum mismatch (expected " + spec.sha256 +
                        ", got " + actual + ")"};
        }
    }
    return core::Status::Ok();
}

}  // namespace

core::Status ModelRunner::create(const ModelSpec& spec,
                                 std::unique_ptr<ModelRunner>& out) {
    if (auto st = validateArtifact(spec); !st.ok()) {
        return st;
    }
    switch (spec.backend) {
        case BackendKind::kReference:
            out = std::make_unique<ReferenceRunner>(spec);
            return core::Status::Ok();
        case BackendKind::kOnnxRuntime:
#ifdef IMGPROC_WITH_ONNX
            return makeOnnxRunner(spec, out);
#else
            return core::Unsupported(
                "ONNX Runtime backend not compiled (configure with -DIMGPROC_WITH_ONNX=ON)");
#endif
        case BackendKind::kOpenCvDnn:
        case BackendKind::kTensorRt:
            // Additional accelerated backends are compiled in behind feature
            // flags; they are not part of the dependency-light default build.
            return core::Unsupported(
                "requested ML backend not compiled in this build");
    }
    return core::InvalidArgument("unknown BackendKind");
}

}  // namespace imgproc::ml
