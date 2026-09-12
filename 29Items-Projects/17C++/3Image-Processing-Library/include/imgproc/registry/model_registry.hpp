// imgproc/registry/model_registry.hpp
// Relational model registry backing "ML model integration": tracks registered
// model artifacts, their class labels, and a log of detection-run provenance.
// Backed by an embedded SQLite database (see migrations/0001_init_model_registry.sql).
//
// Provides full CRUD for all three entities. The SQLite handle is hidden behind
// a pimpl so this public header pulls in no third-party headers.
#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>

#include "imgproc/core/status.hpp"

namespace imgproc::registry {

// ---- Entities --------------------------------------------------------------

struct ModelRecord {
    std::int64_t id{0};
    std::string name;            ///< e.g. "yolov8n"
    std::string version;         ///< semver, e.g. "1.3.0"
    std::string backend;         ///< "reference" | "onnxruntime" | "tensorrt" ...
    std::string artifact_path;
    std::string sha256;
    std::string input_shape;     ///< JSON, e.g. "[1,3,640,640]"
    std::string created_at;      ///< populated on read
};

struct LabelRecord {
    std::int64_t id{0};
    std::int64_t model_id{0};
    int class_id{0};
    std::string label;
};

struct DetectionRunRecord {
    std::int64_t id{0};
    std::int64_t model_id{0};
    std::string source_uri;
    std::string device;          ///< "cpu" | "cuda:0" ...
    int num_detections{0};
    double latency_ms{0.0};
    std::string created_at;      ///< populated on read
};

// ---- Repository ------------------------------------------------------------

/// Thread-compatible (not thread-safe) handle to the registry database.
class ModelRegistry {
public:
    ~ModelRegistry();
    ModelRegistry(ModelRegistry&&) noexcept;
    ModelRegistry& operator=(ModelRegistry&&) noexcept;
    ModelRegistry(const ModelRegistry&) = delete;
    ModelRegistry& operator=(const ModelRegistry&) = delete;

    /// Open (creating if needed) the registry at `path` and apply migrations.
    /// Use ":memory:" for an ephemeral in-memory database (handy in tests).
    static core::Status open(const std::string& path,
                             std::unique_ptr<ModelRegistry>& out);

    // --- models: CRUD ---
    [[nodiscard]] core::Status createModel(const ModelRecord& rec, std::int64_t& out_id);
    [[nodiscard]] core::Status getModel(std::int64_t id, ModelRecord& out) const;
    [[nodiscard]] core::Status findModel(const std::string& name,
                                         const std::string& version,
                                         ModelRecord& out) const;
    [[nodiscard]] core::Status listModels(std::vector<ModelRecord>& out) const;
    [[nodiscard]] core::Status updateModel(const ModelRecord& rec);
    [[nodiscard]] core::Status deleteModel(std::int64_t id);

    // --- labels: replace + read (1 model : N labels) ---
    [[nodiscard]] core::Status setLabels(std::int64_t model_id,
                                         const std::vector<LabelRecord>& labels);
    [[nodiscard]] core::Status getLabels(std::int64_t model_id,
                                         std::vector<LabelRecord>& out) const;

    // --- detection_runs: create + read (provenance log) ---
    [[nodiscard]] core::Status createRun(const DetectionRunRecord& rec,
                                         std::int64_t& out_id);
    [[nodiscard]] core::Status getRun(std::int64_t id, DetectionRunRecord& out) const;
    /// List runs for a model, newest first. `limit <= 0` returns all rows;
    /// otherwise results are paginated by `limit`/`offset`.
    [[nodiscard]] core::Status listRuns(std::int64_t model_id,
                                        std::vector<DetectionRunRecord>& out,
                                        int limit = 0, int offset = 0) const;

private:
    ModelRegistry();
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

}  // namespace imgproc::registry
