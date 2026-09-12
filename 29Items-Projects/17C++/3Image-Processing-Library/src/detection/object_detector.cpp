// src/detection/object_detector.cpp
#include "imgproc/detection/object_detector.hpp"

#include <queue>
#include <utility>
#include <vector>

#include "imgproc/core/geometry.hpp"
#include "imgproc/filters/gaussian_blur.hpp"

namespace imgproc::detection {

namespace {

std::string labelFor(const DetectorConfig& cfg, int class_id) {
    if (class_id >= 0 && static_cast<std::size_t>(class_id) < cfg.class_labels.size()) {
        return cfg.class_labels[static_cast<std::size_t>(class_id)];
    }
    return "object";
}

/// Classical detector: grayscale -> (optional blur) -> threshold ->
/// 8-connected components -> bounding boxes -> NMS.
class ClassicalBlobDetector final : public ObjectDetector {
public:
    explicit ClassicalBlobDetector(DetectorConfig cfg) : cfg_(std::move(cfg)) {}

    core::Status detect(const core::Image& image, core::Detections& out) override {
        if (image.empty()) {
            return core::InvalidArgument("detect: empty image");
        }

        // 1. Grayscale.
        core::Image gray;
        if (image.format() == core::PixelFormat::kGray8) {
            gray = image.clone();
        } else if (auto st = image.convertTo(core::PixelFormat::kGray8, gray); !st.ok()) {
            return st;
        }

        // 2. Optional denoise.
        if (cfg_.denoise) {
            core::Image blurred;
            filters::GaussianBlurParams p;
            p.kernel_size = 3;
            p.sigma = 1.0F;
            if (auto st = filters::gaussianBlur(gray, blurred, p); st.ok()) {
                gray = std::move(blurred);
            }
        }

        const int h = gray.height();
        const int w = gray.width();

        // 3. Threshold into a foreground mask.
        std::vector<std::uint8_t> fg(static_cast<std::size_t>(h) * static_cast<std::size_t>(w), 0);
        for (int y = 0; y < h; ++y) {
            for (int x = 0; x < w; ++x) {
                fg[static_cast<std::size_t>(y) * static_cast<std::size_t>(w) +
                   static_cast<std::size_t>(x)] =
                    gray.at(y, x, 0) >= cfg_.blob_threshold ? 1U : 0U;
            }
        }

        // 4. 8-connected components via BFS; accumulate per-component stats.
        core::Detections raw;
        std::vector<std::uint8_t> visited(fg.size(), 0);
        const auto idx = [w](int y, int x) {
            return static_cast<std::size_t>(y) * static_cast<std::size_t>(w) +
                   static_cast<std::size_t>(x);
        };

        for (int sy = 0; sy < h; ++sy) {
            for (int sx = 0; sx < w; ++sx) {
                if (fg[idx(sy, sx)] == 0 || visited[idx(sy, sx)] != 0) {
                    continue;
                }
                int minx = sx, maxx = sx, miny = sy, maxy = sy;
                long area = 0;
                double intensity_sum = 0.0;

                std::queue<std::pair<int, int>> q;
                q.emplace(sy, sx);
                visited[idx(sy, sx)] = 1;
                while (!q.empty()) {
                    const auto [cy, cx] = q.front();
                    q.pop();
                    ++area;
                    intensity_sum += static_cast<double>(gray.at(cy, cx, 0));
                    minx = std::min(minx, cx);
                    maxx = std::max(maxx, cx);
                    miny = std::min(miny, cy);
                    maxy = std::max(maxy, cy);
                    for (int dy = -1; dy <= 1; ++dy) {
                        for (int dx = -1; dx <= 1; ++dx) {
                            if (dx == 0 && dy == 0) continue;
                            const int ny = cy + dy;
                            const int nx = cx + dx;
                            if (ny < 0 || ny >= h || nx < 0 || nx >= w) continue;
                            if (fg[idx(ny, nx)] != 0 && visited[idx(ny, nx)] == 0) {
                                visited[idx(ny, nx)] = 1;
                                q.emplace(ny, nx);
                            }
                        }
                    }
                }

                if (area < cfg_.min_blob_area) {
                    continue;
                }
                core::Detection d;
                d.box = core::BBox{static_cast<float>(minx), static_cast<float>(miny),
                                   static_cast<float>(maxx - minx + 1),
                                   static_cast<float>(maxy - miny + 1)};
                d.class_id = 0;
                d.score = static_cast<float>(intensity_sum / static_cast<double>(area) / 255.0);
                d.label = labelFor(cfg_, 0);
                raw.push_back(std::move(d));
            }
        }

        // 5. Score threshold + NMS.
        core::Detections filtered;
        for (auto& d : raw) {
            if (d.score >= cfg_.score_threshold) {
                filtered.push_back(std::move(d));
            }
        }
        out = core::nonMaxSuppression(filtered, cfg_.nms_iou_threshold);
        return core::Status::Ok();
    }

private:
    DetectorConfig cfg_;
};

/// Model-backed detector: preprocess -> ml::ModelRunner -> decode + NMS.
/// Uses the reference runner by default so it is exercisable without a real
/// network; swap ModelSpec.backend for ONNX/TensorRT in production.
class ModelDetector final : public ObjectDetector {
public:
    ModelDetector(DetectorConfig cfg, std::unique_ptr<ml::ModelRunner> runner)
        : cfg_(std::move(cfg)), runner_(std::move(runner)) {}

    core::Status detect(const core::Image& image, core::Detections& out) override {
        if (image.empty()) {
            return core::InvalidArgument("detect: empty image");
        }
        // Preprocess: grayscale -> NCHW float tensor at the model input size.
        core::Image gray;
        if (image.format() == core::PixelFormat::kGray8) {
            gray = image.clone();
        } else if (auto st = image.convertTo(core::PixelFormat::kGray8, gray); !st.ok()) {
            return st;
        }
        const auto& shape = cfg_.model.input_shape;  // {N,C,H,W}
        const int net_h = shape.size() == 4 ? shape[2] : gray.height();
        const int net_w = shape.size() == 4 ? shape[3] : gray.width();

        core::Tensor input({1, 1, net_h, net_w}, core::Device::kCpu);
        float* in = input.host_data();
        for (int y = 0; y < net_h; ++y) {
            for (int x = 0; x < net_w; ++x) {
                // Nearest-neighbour resize + normalize to [0,1].
                const int sy = gray.height() * y / net_h;
                const int sx = gray.width() * x / net_w;
                in[static_cast<std::size_t>(y) * static_cast<std::size_t>(net_w) +
                   static_cast<std::size_t>(x)] =
                    static_cast<float>(gray.at(sy, sx, 0)) / 255.0F;
            }
        }

        core::Tensor output;
        if (auto st = runner_->infer(input, output); !st.ok()) {
            return st;
        }

        // Decode: treat the runner output as a per-pixel objectness map and
        // emit a detection for the strongest activation (sufficient to wire the
        // model path end-to-end; replace with the real head decoder for YOLO).
        const float* o = output.host_data();
        std::size_t best = 0;
        float best_v = -1.0F;
        for (std::size_t i = 0; i < output.numel(); ++i) {
            if (o[i] > best_v) {
                best_v = o[i];
                best = i;
            }
        }
        core::Detections dets;
        if (best_v >= cfg_.score_threshold && net_w > 0) {
            const int by = static_cast<int>(best) / net_w;
            const int bx = static_cast<int>(best) % net_w;
            const float sx_ratio = static_cast<float>(image.width()) / static_cast<float>(net_w);
            const float sy_ratio = static_cast<float>(image.height()) / static_cast<float>(net_h);
            core::Detection d;
            d.box = core::BBox{static_cast<float>(bx) * sx_ratio,
                               static_cast<float>(by) * sy_ratio,
                               sx_ratio, sy_ratio};
            d.class_id = 0;
            d.score = best_v;
            d.label = labelFor(cfg_, 0);
            dets.push_back(std::move(d));
        }
        out = core::nonMaxSuppression(dets, cfg_.nms_iou_threshold);
        return core::Status::Ok();
    }

private:
    DetectorConfig cfg_;
    std::unique_ptr<ml::ModelRunner> runner_;
};

}  // namespace

core::Status ObjectDetector::create(const DetectorConfig& config,
                                    std::unique_ptr<ObjectDetector>& out) {
    switch (config.backend) {
        case DetectorBackend::kClassicalBlob:
            out = std::make_unique<ClassicalBlobDetector>(config);
            return core::Status::Ok();
        case DetectorBackend::kModel: {
            std::unique_ptr<ml::ModelRunner> runner;
            if (auto st = ml::ModelRunner::create(config.model, runner); !st.ok()) {
                return st;
            }
            out = std::make_unique<ModelDetector>(config, std::move(runner));
            return core::Status::Ok();
        }
    }
    return core::InvalidArgument("unknown DetectorBackend");
}

}  // namespace imgproc::detection
