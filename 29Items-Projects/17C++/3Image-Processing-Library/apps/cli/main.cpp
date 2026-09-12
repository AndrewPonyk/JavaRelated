// apps/cli/main.cpp
// Reference CLI demonstrating the full pipeline:
//   decode (PPM) -> detect -> track -> annotate -> optional registry logging.
//
//   imgproc-cli --input frame.ppm [--output out.ppm] [--registry reg.db]
//              [--threshold 0.25] [--blob 180] [--device cpu]

// Allow the standard std::getenv on MSVC (it flags it C4996 "unsafe").
#define _CRT_SECURE_NO_WARNINGS

#include <chrono>
#include <cstdlib>
#include <iostream>
#include <stdexcept>
#include <string>

#include "imgproc/core/types.hpp"
#include "imgproc/cuda/cuda_utils.hpp"
#include "imgproc/detection/object_detector.hpp"
#include "imgproc/io/image_io.hpp"
#include "imgproc/registry/model_registry.hpp"
#include "imgproc/tracking/tracker.hpp"
#include "imgproc/version.hpp"

namespace ip = imgproc;

namespace {

// Read an environment variable, returning `fallback` when unset/empty.
std::string envOr(const char* name, const std::string& fallback) {
    const char* v = std::getenv(name);
    return (v && *v) ? std::string(v) : fallback;
}

struct Args {
    std::string input;
    std::string output;
    std::string registry;
    std::string device = envOr("IMGPROC_DEVICE", "cpu");
    std::string model_dir = envOr("IMGPROC_MODEL_DIR", "");
    float threshold = 0.25F;
    int blob = 180;
};

void printUsage(const char* prog) {
    std::cerr << "imgproc-cli " << ip::kVersionString << "\n"
              << "Usage: " << prog << " --input <file.ppm|.pgm> [options]\n"
              << "  --output <file.ppm>   write an annotated image\n"
              << "  --registry <file.db>  log the run to a SQLite registry\n"
              << "  --threshold <float>   detection score threshold (default 0.25)\n"
              << "  --blob <0-255>        brightness threshold (default 180)\n"
              << "  --device <cpu|cuda:N> compute device (default cpu, env IMGPROC_DEVICE)\n"
              << "  --model-dir <dir>     allow-listed model directory (env IMGPROC_MODEL_DIR)\n";
}

// Parse argv into `a`. Returns false (and prints a reason) on any error so the
// CLI fails gracefully instead of terminating on a bad numeric argument.
bool parseArgs(int argc, char** argv, Args& a) {
    for (int i = 1; i < argc; ++i) {
        const std::string s = argv[i];
        auto next = [&]() -> std::string { return (i + 1 < argc) ? argv[++i] : std::string{}; };
        try {
            if (s == "--input") a.input = next();
            else if (s == "--output") a.output = next();
            else if (s == "--registry") a.registry = next();
            else if (s == "--device") a.device = next();
            else if (s == "--model-dir") a.model_dir = next();
            else if (s == "--threshold") a.threshold = std::stof(next());
            else if (s == "--blob") a.blob = std::stoi(next());
            else if (s == "-h" || s == "--help") return false;
            else { std::cerr << "Unknown argument: " << s << "\n"; return false; }
        } catch (const std::exception&) {
            std::cerr << "Invalid value for " << s << "\n";
            return false;
        }
    }
    if (a.input.empty()) {
        std::cerr << "Error: --input is required\n";
        return false;
    }
    if (a.threshold < 0.0F || a.threshold > 1.0F) {
        std::cerr << "Error: --threshold must be in [0, 1]\n";
        return false;
    }
    if (a.blob < 0 || a.blob > 255) {
        std::cerr << "Error: --blob must be in [0, 255]\n";
        return false;
    }
    return true;
}

// Draw a 1px rectangle outline (white) onto a color/gray image.
void drawBox(ip::core::Image& img, const ip::core::BBox& b) {
    const int x0 = std::max(0, static_cast<int>(b.x));
    const int y0 = std::max(0, static_cast<int>(b.y));
    const int x1 = std::min(img.width() - 1, static_cast<int>(b.right()));
    const int y1 = std::min(img.height() - 1, static_cast<int>(b.bottom()));
    const int c = img.channels();
    for (int x = x0; x <= x1; ++x) {
        for (int ch = 0; ch < c; ++ch) {
            img.at(y0, x, ch) = 255;
            img.at(y1, x, ch) = 255;
        }
    }
    for (int y = y0; y <= y1; ++y) {
        for (int ch = 0; ch < c; ++ch) {
            img.at(y, x0, ch) = 255;
            img.at(y, x1, ch) = 255;
        }
    }
}

}  // namespace

int main(int argc, char** argv) {
    Args args;
    if (!parseArgs(argc, argv, args)) {
        printUsage(argv[0]);
        return EXIT_FAILURE;
    }

    const bool want_cuda = args.device.rfind("cuda", 0) == 0;
    const auto device =
        (want_cuda && ip::cuda::isAvailable()) ? ip::core::Device::kCuda : ip::core::Device::kCpu;
    if (want_cuda && device == ip::core::Device::kCpu) {
        std::cerr << "[warn] CUDA requested but unavailable; using CPU.\n";
    }

    std::cout << "[info] loading " << args.input << "\n";
    ip::core::Image frame;
    if (const auto st = ip::io::readImage(args.input, frame); !st.ok()) {
        std::cerr << "[error] " << st.message() << "\n";
        return EXIT_FAILURE;
    }
    std::cout << "[info] frame " << frame.width() << "x" << frame.height() << " ch="
              << frame.channels() << "\n";

    ip::detection::DetectorConfig cfg;
    cfg.score_threshold = args.threshold;
    cfg.blob_threshold = static_cast<std::uint8_t>(args.blob);
    cfg.model.model_dir = args.model_dir;  // allow-list root for model artifacts
    cfg.model.device = device;
    std::unique_ptr<ip::detection::ObjectDetector> detector;
    if (const auto st = ip::detection::ObjectDetector::create(cfg, detector); !st.ok()) {
        std::cerr << "[error] detector: " << st.message() << "\n";
        return EXIT_FAILURE;
    }

    const auto t0 = std::chrono::steady_clock::now();
    ip::core::Detections dets;
    if (const auto st = detector->detect(frame, dets); !st.ok()) {
        std::cerr << "[error] " << st.message() << "\n";
        return EXIT_FAILURE;
    }
    ip::tracking::Tracker tracker;
    ip::core::Tracks tracks;
    if (const auto st = tracker.update(dets, tracks); !st.ok()) {
        std::cerr << "[error] track: " << st.message() << "\n";
        return EXIT_FAILURE;
    }
    const auto t1 = std::chrono::steady_clock::now();
    const double latency_ms =
        std::chrono::duration<double, std::milli>(t1 - t0).count();

    std::cout << "[info] detections=" << dets.size() << " tracks=" << tracks.size()
              << " latency=" << latency_ms << "ms\n";
    for (const auto& d : dets) {
        std::cout << "  - " << d.label << " score=" << d.score << " box=[" << d.box.x
                  << "," << d.box.y << "," << d.box.width << "," << d.box.height << "]\n";
    }

    if (!args.output.empty()) {
        ip::core::Image annotated = frame.clone();
        for (const auto& d : dets) {
            drawBox(annotated, d.box);
        }
        if (const auto st = ip::io::writeImage(args.output, annotated); !st.ok()) {
            std::cerr << "[error] " << st.message() << "\n";
            return EXIT_FAILURE;
        }
        std::cout << "[info] wrote " << args.output << "\n";
    }

    if (!args.registry.empty()) {
        std::unique_ptr<ip::registry::ModelRegistry> reg;
        if (const auto st = ip::registry::ModelRegistry::open(args.registry, reg); !st.ok()) {
            std::cerr << "[error] registry open: " << st.message() << "\n";
            return EXIT_FAILURE;
        }
        // Ensure the classical detector is registered, then log the run.
        ip::registry::ModelRecord model;
        if (!reg->findModel("classical-blob", "1.0.0", model).ok()) {
            ip::registry::ModelRecord m;
            m.name = "classical-blob";
            m.version = "1.0.0";
            m.backend = "reference";
            m.artifact_path = "(builtin)";
            m.sha256 = "";
            m.input_shape = "[]";
            std::int64_t id = 0;
            if (const auto st = reg->createModel(m, id); !st.ok()) {
                std::cerr << "[error] registry: " << st.message() << "\n";
                return EXIT_FAILURE;
            }
            model.id = id;
        }
        ip::registry::DetectionRunRecord run;
        run.model_id = model.id;
        run.source_uri = args.input;
        run.device = (device == ip::core::Device::kCuda) ? "cuda:0" : "cpu";
        run.num_detections = static_cast<int>(dets.size());
        run.latency_ms = latency_ms;
        std::int64_t run_id = 0;
        if (const auto st = reg->createRun(run, run_id); !st.ok()) {
            std::cerr << "[error] registry: " << st.message() << "\n";
            return EXIT_FAILURE;
        }
        std::cout << "[info] logged run #" << run_id << " to " << args.registry << "\n";
    }

    return EXIT_SUCCESS;
}
