// src/ml/onnx_runner.cpp
// Real ONNX Runtime inference backend (compiled only with IMGPROC_WITH_ONNX).
// Loads an .onnx model and runs forward passes through an Ort::Session.
#include "onnx_backend.hpp"

#include <onnxruntime_cxx_api.h>

#include <algorithm>
#include <string>
#include <vector>

namespace imgproc::ml {

namespace {

#ifdef _WIN32
// ONNX Runtime takes wchar_t* paths on Windows. Model paths here are ASCII.
std::wstring toOrtPath(const std::string& s) { return std::wstring(s.begin(), s.end()); }
#endif

/// Process-wide Ort environment (must outlive every session).
Ort::Env& ortEnv() {
    static Ort::Env env(ORT_LOGGING_LEVEL_WARNING, "imgproc");
    return env;
}

class OnnxRunner final : public ModelRunner {
public:
    OnnxRunner(ModelSpec spec, Ort::Session session)
        : spec_(std::move(spec)), session_(std::move(session)) {
        Ort::AllocatorWithDefaultOptions alloc;
        for (std::size_t i = 0; i < session_.GetInputCount(); ++i) {
            input_names_owned_.push_back(session_.GetInputNameAllocated(i, alloc));
            input_names_.push_back(input_names_owned_.back().get());
        }
        for (std::size_t i = 0; i < session_.GetOutputCount(); ++i) {
            output_names_owned_.push_back(session_.GetOutputNameAllocated(i, alloc));
            output_names_.push_back(output_names_owned_.back().get());
        }
    }

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
        if (input.numel() == 0 || input.host_data() == nullptr) {
            return core::InvalidArgument("infer: empty input tensor");
        }
        if (input_names_.empty() || output_names_.empty()) {
            return {core::StatusCode::kModelError, "model has no inputs/outputs"};
        }
        try {
            const auto mem = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
            std::vector<std::int64_t> shape(input.shape().begin(), input.shape().end());

            Ort::Value in_val = Ort::Value::CreateTensor<float>(
                mem, const_cast<float*>(input.host_data()), input.numel(),
                shape.data(), shape.size());

            auto outputs = session_.Run(Ort::RunOptions{nullptr}, input_names_.data(),
                                        &in_val, 1, output_names_.data(),
                                        output_names_.size());
            if (outputs.empty() || !outputs.front().IsTensor()) {
                return {core::StatusCode::kModelError, "model produced no tensor output"};
            }

            const auto info = outputs.front().GetTensorTypeAndShapeInfo();
            if (info.GetElementType() != ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT) {
                return core::Unsupported("only float32 model outputs are supported");
            }
            const auto out_shape64 = info.GetShape();
            std::vector<int> out_shape(out_shape64.begin(), out_shape64.end());

            core::Tensor result(out_shape, core::Device::kCpu);
            const float* src = outputs.front().GetTensorData<float>();
            std::copy(src, src + result.numel(), result.host_data());
            output = std::move(result);
            return core::Status::Ok();
        } catch (const Ort::Exception& e) {
            return {core::StatusCode::kModelError, std::string("ONNX infer: ") + e.what()};
        }
    }

    [[nodiscard]] BackendKind kind() const override { return BackendKind::kOnnxRuntime; }

private:
    ModelSpec spec_;
    Ort::Session session_;
    std::vector<Ort::AllocatedStringPtr> input_names_owned_;
    std::vector<Ort::AllocatedStringPtr> output_names_owned_;
    std::vector<const char*> input_names_;
    std::vector<const char*> output_names_;
};

}  // namespace

core::Status makeOnnxRunner(const ModelSpec& spec, std::unique_ptr<ModelRunner>& out) {
    try {
        Ort::SessionOptions opts;
        opts.SetIntraOpNumThreads(1);
        opts.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);

        // CUDA execution provider is available only with the GPU build of ONNX
        // Runtime; appending it here is a no-op fallback for the CPU package.
        if (spec.device == core::Device::kCuda) {
            try {
                OrtCUDAProviderOptions cuda_opts{};
                opts.AppendExecutionProvider_CUDA(cuda_opts);
            } catch (const Ort::Exception&) {
                // CPU package: silently fall back to the CPU provider.
            }
        }

#ifdef _WIN32
        const std::wstring path = toOrtPath(spec.artifact_path);
        Ort::Session session(ortEnv(), path.c_str(), opts);
#else
        Ort::Session session(ortEnv(), spec.artifact_path.c_str(), opts);
#endif
        out = std::make_unique<OnnxRunner>(spec, std::move(session));
        return core::Status::Ok();
    } catch (const Ort::Exception& e) {
        return {core::StatusCode::kModelError, std::string("ONNX load: ") + e.what()};
    }
}

}  // namespace imgproc::ml
