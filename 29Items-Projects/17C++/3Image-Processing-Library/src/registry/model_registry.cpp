// src/registry/model_registry.cpp
#include "imgproc/registry/model_registry.hpp"

#include <sqlite3.h>

#include <string>

namespace imgproc::registry {

namespace {

// Schema kept in lock-step with migrations/0001_init_model_registry.sql.
constexpr const char* kSchema = R"sql(
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS models (
    id            INTEGER PRIMARY KEY,
    name          TEXT NOT NULL,
    version       TEXT NOT NULL,
    backend       TEXT NOT NULL,
    artifact_path TEXT NOT NULL,
    sha256        TEXT NOT NULL,
    input_shape   TEXT NOT NULL,
    created_at    TEXT NOT NULL DEFAULT (CURRENT_TIMESTAMP),
    UNIQUE (name, version)
);
CREATE INDEX IF NOT EXISTS idx_models_name ON models (name);

CREATE TABLE IF NOT EXISTS labels (
    id       INTEGER PRIMARY KEY,
    model_id INTEGER NOT NULL REFERENCES models (id) ON DELETE CASCADE,
    class_id INTEGER NOT NULL,
    label    TEXT NOT NULL,
    UNIQUE (model_id, class_id)
);
CREATE INDEX IF NOT EXISTS idx_labels_model ON labels (model_id);

CREATE TABLE IF NOT EXISTS detection_runs (
    id             INTEGER PRIMARY KEY,
    model_id       INTEGER NOT NULL REFERENCES models (id) ON DELETE RESTRICT,
    source_uri     TEXT,
    device         TEXT NOT NULL,
    num_detections INTEGER NOT NULL DEFAULT 0,
    latency_ms     REAL,
    created_at     TEXT NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);
CREATE INDEX IF NOT EXISTS idx_runs_model   ON detection_runs (model_id);
CREATE INDEX IF NOT EXISTS idx_runs_created ON detection_runs (created_at);
)sql";

std::string colText(sqlite3_stmt* st, int col) {
    const auto* p = sqlite3_column_text(st, col);
    return p ? reinterpret_cast<const char*>(p) : std::string{};
}

}  // namespace

struct ModelRegistry::Impl {
    sqlite3* db{nullptr};

    core::Status err(const std::string& what) const {
        return {core::StatusCode::kInternal,
                what + ": " + (db ? sqlite3_errmsg(db) : "no db")};
    }
};

ModelRegistry::ModelRegistry() : impl_(std::make_unique<Impl>()) {}

ModelRegistry::~ModelRegistry() {
    if (impl_ && impl_->db) {
        sqlite3_close(impl_->db);
    }
}
ModelRegistry::ModelRegistry(ModelRegistry&&) noexcept = default;
ModelRegistry& ModelRegistry::operator=(ModelRegistry&&) noexcept = default;

core::Status ModelRegistry::open(const std::string& path,
                                 std::unique_ptr<ModelRegistry>& out) {
    std::unique_ptr<ModelRegistry> reg(new ModelRegistry());
    if (sqlite3_open(path.c_str(), &reg->impl_->db) != SQLITE_OK) {
        return reg->impl_->err("sqlite3_open");
    }
    sqlite3_busy_timeout(reg->impl_->db, 2000);
    // Enforce foreign keys for this connection (must be set per-connection).
    sqlite3_exec(reg->impl_->db, "PRAGMA foreign_keys=ON;", nullptr, nullptr, nullptr);
    char* errmsg = nullptr;
    if (sqlite3_exec(reg->impl_->db, kSchema, nullptr, nullptr, &errmsg) != SQLITE_OK) {
        const std::string m = errmsg ? errmsg : "unknown";
        sqlite3_free(errmsg);
        return {core::StatusCode::kInternal, "migration failed: " + m};
    }
    out = std::move(reg);
    return core::Status::Ok();
}

// ---- models ---------------------------------------------------------------

core::Status ModelRegistry::createModel(const ModelRecord& rec, std::int64_t& out_id) {
    if (rec.name.empty() || rec.version.empty()) {
        return core::InvalidArgument("createModel: name and version are required");
    }
    static const char* kSql =
        "INSERT INTO models (name, version, backend, artifact_path, sha256, input_shape) "
        "VALUES (?,?,?,?,?,?)";
    sqlite3_stmt* st = nullptr;
    if (sqlite3_prepare_v2(impl_->db, kSql, -1, &st, nullptr) != SQLITE_OK) {
        return impl_->err("createModel/prepare");
    }
    sqlite3_bind_text(st, 1, rec.name.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(st, 2, rec.version.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(st, 3, rec.backend.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(st, 4, rec.artifact_path.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(st, 5, rec.sha256.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(st, 6, rec.input_shape.c_str(), -1, SQLITE_TRANSIENT);
    const int rc = sqlite3_step(st);
    sqlite3_finalize(st);
    if (rc != SQLITE_DONE) {
        if (rc == SQLITE_CONSTRAINT) {
            return {core::StatusCode::kInvalidArgument,
                    "createModel: (name, version) already exists"};
        }
        return impl_->err("createModel/step");
    }
    out_id = sqlite3_last_insert_rowid(impl_->db);
    return core::Status::Ok();
}

namespace {
void fillModel(sqlite3_stmt* st, ModelRecord& m) {
    m.id = sqlite3_column_int64(st, 0);
    m.name = colText(st, 1);
    m.version = colText(st, 2);
    m.backend = colText(st, 3);
    m.artifact_path = colText(st, 4);
    m.sha256 = colText(st, 5);
    m.input_shape = colText(st, 6);
    m.created_at = colText(st, 7);
}
constexpr const char* kModelCols =
    "id,name,version,backend,artifact_path,sha256,input_shape,created_at";
}  // namespace

core::Status ModelRegistry::getModel(std::int64_t id, ModelRecord& out) const {
    const std::string sql =
        std::string("SELECT ") + kModelCols + " FROM models WHERE id = ?";
    sqlite3_stmt* st = nullptr;
    if (sqlite3_prepare_v2(impl_->db, sql.c_str(), -1, &st, nullptr) != SQLITE_OK) {
        return impl_->err("getModel/prepare");
    }
    sqlite3_bind_int64(st, 1, id);
    const int rc = sqlite3_step(st);
    if (rc == SQLITE_ROW) {
        fillModel(st, out);
        sqlite3_finalize(st);
        return core::Status::Ok();
    }
    sqlite3_finalize(st);
    return {core::StatusCode::kNotFound, "getModel: no model with id " + std::to_string(id)};
}

core::Status ModelRegistry::findModel(const std::string& name, const std::string& version,
                                      ModelRecord& out) const {
    const std::string sql = std::string("SELECT ") + kModelCols +
                            " FROM models WHERE name = ? AND version = ?";
    sqlite3_stmt* st = nullptr;
    if (sqlite3_prepare_v2(impl_->db, sql.c_str(), -1, &st, nullptr) != SQLITE_OK) {
        return impl_->err("findModel/prepare");
    }
    sqlite3_bind_text(st, 1, name.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(st, 2, version.c_str(), -1, SQLITE_TRANSIENT);
    const int rc = sqlite3_step(st);
    if (rc == SQLITE_ROW) {
        fillModel(st, out);
        sqlite3_finalize(st);
        return core::Status::Ok();
    }
    sqlite3_finalize(st);
    return {core::StatusCode::kNotFound, "findModel: " + name + "@" + version + " not found"};
}

core::Status ModelRegistry::listModels(std::vector<ModelRecord>& out) const {
    out.clear();
    const std::string sql =
        std::string("SELECT ") + kModelCols + " FROM models ORDER BY name, version";
    sqlite3_stmt* st = nullptr;
    if (sqlite3_prepare_v2(impl_->db, sql.c_str(), -1, &st, nullptr) != SQLITE_OK) {
        return impl_->err("listModels/prepare");
    }
    while (sqlite3_step(st) == SQLITE_ROW) {
        ModelRecord m;
        fillModel(st, m);
        out.push_back(std::move(m));
    }
    sqlite3_finalize(st);
    return core::Status::Ok();
}

core::Status ModelRegistry::updateModel(const ModelRecord& rec) {
    if (rec.id <= 0) {
        return core::InvalidArgument("updateModel: id is required");
    }
    static const char* kSql =
        "UPDATE models SET name=?, version=?, backend=?, artifact_path=?, "
        "sha256=?, input_shape=? WHERE id=?";
    sqlite3_stmt* st = nullptr;
    if (sqlite3_prepare_v2(impl_->db, kSql, -1, &st, nullptr) != SQLITE_OK) {
        return impl_->err("updateModel/prepare");
    }
    sqlite3_bind_text(st, 1, rec.name.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(st, 2, rec.version.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(st, 3, rec.backend.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(st, 4, rec.artifact_path.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(st, 5, rec.sha256.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(st, 6, rec.input_shape.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int64(st, 7, rec.id);
    const int rc = sqlite3_step(st);
    sqlite3_finalize(st);
    if (rc != SQLITE_DONE) {
        return impl_->err("updateModel/step");
    }
    if (sqlite3_changes(impl_->db) == 0) {
        return {core::StatusCode::kNotFound, "updateModel: no model with id " +
                                                 std::to_string(rec.id)};
    }
    return core::Status::Ok();
}

core::Status ModelRegistry::deleteModel(std::int64_t id) {
    sqlite3_stmt* st = nullptr;
    if (sqlite3_prepare_v2(impl_->db, "DELETE FROM models WHERE id=?", -1, &st, nullptr) !=
        SQLITE_OK) {
        return impl_->err("deleteModel/prepare");
    }
    sqlite3_bind_int64(st, 1, id);
    const int rc = sqlite3_step(st);
    sqlite3_finalize(st);
    if (rc != SQLITE_DONE) {
        return impl_->err("deleteModel/step");
    }
    if (sqlite3_changes(impl_->db) == 0) {
        return {core::StatusCode::kNotFound,
                "deleteModel: no model with id " + std::to_string(id)};
    }
    return core::Status::Ok();
}

// ---- labels ---------------------------------------------------------------

core::Status ModelRegistry::setLabels(std::int64_t model_id,
                                      const std::vector<LabelRecord>& labels) {
    char* err = nullptr;
    if (sqlite3_exec(impl_->db, "BEGIN", nullptr, nullptr, &err) != SQLITE_OK) {
        sqlite3_free(err);
        return impl_->err("setLabels/begin");
    }

    sqlite3_stmt* del = nullptr;
    if (sqlite3_prepare_v2(impl_->db, "DELETE FROM labels WHERE model_id=?", -1, &del,
                           nullptr) != SQLITE_OK) {
        sqlite3_exec(impl_->db, "ROLLBACK", nullptr, nullptr, nullptr);
        return impl_->err("setLabels/delete-prepare");
    }
    sqlite3_bind_int64(del, 1, model_id);
    if (sqlite3_step(del) != SQLITE_DONE) {
        sqlite3_finalize(del);
        sqlite3_exec(impl_->db, "ROLLBACK", nullptr, nullptr, nullptr);
        return impl_->err("setLabels/delete-step");
    }
    sqlite3_finalize(del);

    static const char* kIns =
        "INSERT INTO labels (model_id, class_id, label) VALUES (?,?,?)";
    for (const auto& l : labels) {
        sqlite3_stmt* st = nullptr;
        if (sqlite3_prepare_v2(impl_->db, kIns, -1, &st, nullptr) != SQLITE_OK) {
            sqlite3_exec(impl_->db, "ROLLBACK", nullptr, nullptr, nullptr);
            return impl_->err("setLabels/prepare");
        }
        sqlite3_bind_int64(st, 1, model_id);
        sqlite3_bind_int(st, 2, l.class_id);
        sqlite3_bind_text(st, 3, l.label.c_str(), -1, SQLITE_TRANSIENT);
        const int rc = sqlite3_step(st);
        sqlite3_finalize(st);
        if (rc != SQLITE_DONE) {
            sqlite3_exec(impl_->db, "ROLLBACK", nullptr, nullptr, nullptr);
            return impl_->err("setLabels/step");
        }
    }
    if (sqlite3_exec(impl_->db, "COMMIT", nullptr, nullptr, &err) != SQLITE_OK) {
        sqlite3_free(err);
        return impl_->err("setLabels/commit");
    }
    return core::Status::Ok();
}

core::Status ModelRegistry::getLabels(std::int64_t model_id,
                                      std::vector<LabelRecord>& out) const {
    out.clear();
    sqlite3_stmt* st = nullptr;
    if (sqlite3_prepare_v2(
            impl_->db,
            "SELECT id, model_id, class_id, label FROM labels WHERE model_id=? "
            "ORDER BY class_id",
            -1, &st, nullptr) != SQLITE_OK) {
        return impl_->err("getLabels/prepare");
    }
    sqlite3_bind_int64(st, 1, model_id);
    while (sqlite3_step(st) == SQLITE_ROW) {
        LabelRecord l;
        l.id = sqlite3_column_int64(st, 0);
        l.model_id = sqlite3_column_int64(st, 1);
        l.class_id = sqlite3_column_int(st, 2);
        l.label = colText(st, 3);
        out.push_back(std::move(l));
    }
    sqlite3_finalize(st);
    return core::Status::Ok();
}

// ---- detection_runs -------------------------------------------------------

core::Status ModelRegistry::createRun(const DetectionRunRecord& rec, std::int64_t& out_id) {
    static const char* kSql =
        "INSERT INTO detection_runs (model_id, source_uri, device, num_detections, "
        "latency_ms) VALUES (?,?,?,?,?)";
    sqlite3_stmt* st = nullptr;
    if (sqlite3_prepare_v2(impl_->db, kSql, -1, &st, nullptr) != SQLITE_OK) {
        return impl_->err("createRun/prepare");
    }
    sqlite3_bind_int64(st, 1, rec.model_id);
    sqlite3_bind_text(st, 2, rec.source_uri.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(st, 3, rec.device.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(st, 4, rec.num_detections);
    sqlite3_bind_double(st, 5, rec.latency_ms);
    const int rc = sqlite3_step(st);
    sqlite3_finalize(st);
    if (rc != SQLITE_DONE) {
        if (rc == SQLITE_CONSTRAINT) {
            return core::InvalidArgument("createRun: model_id does not exist");
        }
        return impl_->err("createRun/step");
    }
    out_id = sqlite3_last_insert_rowid(impl_->db);
    return core::Status::Ok();
}

namespace {
void fillRun(sqlite3_stmt* st, DetectionRunRecord& r) {
    r.id = sqlite3_column_int64(st, 0);
    r.model_id = sqlite3_column_int64(st, 1);
    r.source_uri = colText(st, 2);
    r.device = colText(st, 3);
    r.num_detections = sqlite3_column_int(st, 4);
    r.latency_ms = sqlite3_column_double(st, 5);
    r.created_at = colText(st, 6);
}
constexpr const char* kRunCols =
    "id,model_id,source_uri,device,num_detections,latency_ms,created_at";
}  // namespace

core::Status ModelRegistry::getRun(std::int64_t id, DetectionRunRecord& out) const {
    const std::string sql =
        std::string("SELECT ") + kRunCols + " FROM detection_runs WHERE id=?";
    sqlite3_stmt* st = nullptr;
    if (sqlite3_prepare_v2(impl_->db, sql.c_str(), -1, &st, nullptr) != SQLITE_OK) {
        return impl_->err("getRun/prepare");
    }
    sqlite3_bind_int64(st, 1, id);
    const int rc = sqlite3_step(st);
    if (rc == SQLITE_ROW) {
        fillRun(st, out);
        sqlite3_finalize(st);
        return core::Status::Ok();
    }
    sqlite3_finalize(st);
    return {core::StatusCode::kNotFound, "getRun: no run with id " + std::to_string(id)};
}

core::Status ModelRegistry::listRuns(std::int64_t model_id,
                                     std::vector<DetectionRunRecord>& out, int limit,
                                     int offset) const {
    out.clear();
    std::string sql = std::string("SELECT ") + kRunCols +
                      " FROM detection_runs WHERE model_id=? ORDER BY id DESC";
    if (limit > 0) {
        sql += " LIMIT ? OFFSET ?";
    }
    sqlite3_stmt* st = nullptr;
    if (sqlite3_prepare_v2(impl_->db, sql.c_str(), -1, &st, nullptr) != SQLITE_OK) {
        return impl_->err("listRuns/prepare");
    }
    sqlite3_bind_int64(st, 1, model_id);
    if (limit > 0) {
        sqlite3_bind_int(st, 2, limit);
        sqlite3_bind_int(st, 3, offset < 0 ? 0 : offset);
    }
    while (sqlite3_step(st) == SQLITE_ROW) {
        DetectionRunRecord r;
        fillRun(st, r);
        out.push_back(std::move(r));
    }
    sqlite3_finalize(st);
    return core::Status::Ok();
}

}  // namespace imgproc::registry
