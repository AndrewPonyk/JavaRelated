#include "jsengine/storage/metadata_store.h"

#include <sqlite3.h>

#include <algorithm>
#include <cctype>
#include <limits>
#include <utility>

namespace jsengine::storage {

namespace {

constexpr const char* SchemaSql = R"sql(
CREATE TABLE IF NOT EXISTS metadata_kv (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS module_cache_entries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    specifier TEXT NOT NULL,
    resolved_path TEXT NOT NULL,
    format TEXT NOT NULL CHECK (format IN ('esm', 'commonjs')),
    bytecode_hash TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(specifier, resolved_path)
);

CREATE TABLE IF NOT EXISTS runtime_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    category TEXT NOT NULL,
    severity TEXT NOT NULL,
    message TEXT NOT NULL,
    metadata_json TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_module_cache_entries_specifier
    ON module_cache_entries(specifier);

CREATE INDEX IF NOT EXISTS idx_runtime_events_category_created_at
    ON runtime_events(category, created_at);

CREATE INDEX IF NOT EXISTS idx_metadata_kv_updated_at
    ON metadata_kv(updated_at);
)sql";

std::string columnText(sqlite3_stmt* statement, int column) {
    const auto* text = sqlite3_column_text(statement, column);
    return text == nullptr ? "" : reinterpret_cast<const char*>(text);
}

bool isBlank(const std::string& value) {
    return std::all_of(value.begin(), value.end(), [](unsigned char current) { return std::isspace(current) != 0; });
}

bool isSupportedModuleFormat(const std::string& format) {
    return format == "esm" || format == "commonjs";
}

bool isSupportedSeverity(const std::string& severity) {
    return severity == "debug" || severity == "info" || severity == "warn" || severity == "error";
}

} // namespace

MetadataStore::MetadataStore() = default;

MetadataStore::MetadataStore(std::string databasePath) {
    open(databasePath);
}

MetadataStore::~MetadataStore() {
    close();
}

MetadataStore::MetadataStore(MetadataStore&& other) noexcept
    : db_(other.db_), memoryValues_(std::move(other.memoryValues_)), lastError_(std::move(other.lastError_)) {
    other.db_ = nullptr;
}

MetadataStore& MetadataStore::operator=(MetadataStore&& other) noexcept {
    if (this != &other) {
        close();
        db_ = other.db_;
        memoryValues_ = std::move(other.memoryValues_);
        lastError_ = std::move(other.lastError_);
        other.db_ = nullptr;
    }
    return *this;
}

bool MetadataStore::open(const std::string& databasePath) {
    close();
    if (sqlite3_open(databasePath.c_str(), &db_) != SQLITE_OK) {
        lastError_ = db_ == nullptr ? "Unable to open SQLite database" : sqlite3_errmsg(db_);
        close();
        return false;
    }
    const auto initialized = initializeSchema();
    if (initialized) {
        lastError_.clear();
    }
    return initialized;
}

bool MetadataStore::initializeSchema() {
    return execute(SchemaSql);
}

bool MetadataStore::isOpen() const {
    return db_ != nullptr;
}

const std::string& MetadataStore::lastError() const {
    return lastError_;
}

void MetadataStore::put(std::string key, std::string value) {
    if (key.empty() || isBlank(key)) {
        lastError_ = "metadata key must be non-empty";
        return;
    }

    if (db_ == nullptr) {
        memoryValues_[std::move(key)] = std::move(value);
        lastError_.clear();
        return;
    }

    sqlite3_stmt* statement = nullptr;
    constexpr const char* sql = "INSERT INTO metadata_kv(key, value, updated_at) VALUES(?, ?, CURRENT_TIMESTAMP) "
                                "ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = CURRENT_TIMESTAMP";
    if (sqlite3_prepare_v2(db_, sql, -1, &statement, nullptr) != SQLITE_OK) {
        lastError_ = sqlite3_errmsg(db_);
        return;
    }

    sqlite3_bind_text(statement, 1, key.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement, 2, value.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(statement) != SQLITE_DONE) {
        lastError_ = sqlite3_errmsg(db_);
    } else {
        lastError_.clear();
    }
    sqlite3_finalize(statement);
}

std::optional<std::string> MetadataStore::get(const std::string& key) const {
    if (key.empty() || isBlank(key)) {
        return std::nullopt;
    }

    if (db_ == nullptr) {
        const auto found = memoryValues_.find(key);
        if (found == memoryValues_.end()) {
            return std::nullopt;
        }
        return found->second;
    }

    sqlite3_stmt* statement = nullptr;
    if (sqlite3_prepare_v2(db_, "SELECT value FROM metadata_kv WHERE key = ?", -1, &statement, nullptr) != SQLITE_OK) {
        return std::nullopt;
    }

    sqlite3_bind_text(statement, 1, key.c_str(), -1, SQLITE_TRANSIENT);
    std::optional<std::string> result;
    if (sqlite3_step(statement) == SQLITE_ROW) {
        result = columnText(statement, 0);
    }
    sqlite3_finalize(statement);
    return result;
}

bool MetadataStore::remove(const std::string& key) {
    if (key.empty() || isBlank(key)) {
        lastError_ = "metadata key must be non-empty";
        return false;
    }

    if (db_ == nullptr) {
        const auto removed = memoryValues_.erase(key) > 0;
        if (removed) {
            lastError_.clear();
        }
        return removed;
    }

    sqlite3_stmt* statement = nullptr;
    if (sqlite3_prepare_v2(db_, "DELETE FROM metadata_kv WHERE key = ?", -1, &statement, nullptr) != SQLITE_OK) {
        lastError_ = sqlite3_errmsg(db_);
        return false;
    }
    sqlite3_bind_text(statement, 1, key.c_str(), -1, SQLITE_TRANSIENT);
    const auto result = sqlite3_step(statement) == SQLITE_DONE && sqlite3_changes(db_) > 0;
    if (result) {
        lastError_.clear();
    }
    sqlite3_finalize(statement);
    return result;
}

std::optional<ModuleCacheEntry> MetadataStore::createModuleCacheEntry(ModuleCacheEntry entry) {
    if (db_ == nullptr) {
        lastError_ = "SQLite database is not open";
        return std::nullopt;
    }
    if (entry.specifier.empty() || isBlank(entry.specifier) || entry.resolvedPath.empty() ||
        isBlank(entry.resolvedPath)) {
        lastError_ = "module cache specifier and resolved path are required";
        return std::nullopt;
    }
    if (!isSupportedModuleFormat(entry.format)) {
        lastError_ = "module cache format must be 'esm' or 'commonjs'";
        return std::nullopt;
    }

    sqlite3_stmt* statement = nullptr;
    constexpr const char* sql =
        "INSERT INTO module_cache_entries(specifier, resolved_path, format, bytecode_hash) VALUES(?, ?, ?, ?)";
    if (sqlite3_prepare_v2(db_, sql, -1, &statement, nullptr) != SQLITE_OK) {
        lastError_ = sqlite3_errmsg(db_);
        return std::nullopt;
    }

    sqlite3_bind_text(statement, 1, entry.specifier.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement, 2, entry.resolvedPath.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement, 3, entry.format.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement, 4, entry.bytecodeHash.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(statement) != SQLITE_DONE) {
        lastError_ = sqlite3_errmsg(db_);
        sqlite3_finalize(statement);
        return std::nullopt;
    }
    sqlite3_finalize(statement);

    entry.id = static_cast<int>(sqlite3_last_insert_rowid(db_));
    lastError_.clear();
    return entry;
}

std::vector<ModuleCacheEntry> MetadataStore::listModuleCacheEntries(ListOptions options) const {
    std::vector<ModuleCacheEntry> entries;
    if (db_ == nullptr || options.limit == 0 || options.limit > 500 ||
        options.offset > static_cast<std::size_t>(std::numeric_limits<int>::max())) {
        return entries;
    }

    sqlite3_stmt* statement = nullptr;
    constexpr const char* sql = "SELECT id, specifier, resolved_path, format, COALESCE(bytecode_hash, '') FROM "
                                "module_cache_entries ORDER BY id LIMIT ? OFFSET ?";
    if (sqlite3_prepare_v2(db_, sql, -1, &statement, nullptr) != SQLITE_OK) {
        return entries;
    }

    sqlite3_bind_int(statement, 1, static_cast<int>(options.limit));
    sqlite3_bind_int(statement, 2, static_cast<int>(options.offset));
    while (sqlite3_step(statement) == SQLITE_ROW) {
        entries.push_back({.id = sqlite3_column_int(statement, 0),
                           .specifier = columnText(statement, 1),
                           .resolvedPath = columnText(statement, 2),
                           .format = columnText(statement, 3),
                           .bytecodeHash = columnText(statement, 4)});
    }
    sqlite3_finalize(statement);
    return entries;
}

std::optional<ModuleCacheEntry> MetadataStore::getModuleCacheEntry(int id) const {
    if (db_ == nullptr || id <= 0) {
        return std::nullopt;
    }

    sqlite3_stmt* statement = nullptr;
    constexpr const char* sql = "SELECT id, specifier, resolved_path, format, COALESCE(bytecode_hash, '') FROM "
                                "module_cache_entries WHERE id = ?";
    if (sqlite3_prepare_v2(db_, sql, -1, &statement, nullptr) != SQLITE_OK) {
        return std::nullopt;
    }

    sqlite3_bind_int(statement, 1, id);
    std::optional<ModuleCacheEntry> result;
    if (sqlite3_step(statement) == SQLITE_ROW) {
        result = ModuleCacheEntry{.id = sqlite3_column_int(statement, 0),
                                  .specifier = columnText(statement, 1),
                                  .resolvedPath = columnText(statement, 2),
                                  .format = columnText(statement, 3),
                                  .bytecodeHash = columnText(statement, 4)};
    }
    sqlite3_finalize(statement);
    return result;
}

bool MetadataStore::deleteModuleCacheEntry(int id) {
    if (db_ == nullptr || id <= 0) {
        return false;
    }

    sqlite3_stmt* statement = nullptr;
    if (sqlite3_prepare_v2(db_, "DELETE FROM module_cache_entries WHERE id = ?", -1, &statement, nullptr) !=
        SQLITE_OK) {
        lastError_ = sqlite3_errmsg(db_);
        return false;
    }
    sqlite3_bind_int(statement, 1, id);
    const auto result = sqlite3_step(statement) == SQLITE_DONE && sqlite3_changes(db_) > 0;
    if (result) {
        lastError_.clear();
    }
    sqlite3_finalize(statement);
    return result;
}

std::optional<RuntimeEvent> MetadataStore::recordRuntimeEvent(RuntimeEvent event) {
    if (db_ == nullptr) {
        lastError_ = "SQLite database is not open";
        return std::nullopt;
    }
    if (event.category.empty() || isBlank(event.category) || event.message.empty() || isBlank(event.message)) {
        lastError_ = "runtime event category and message are required";
        return std::nullopt;
    }
    if (!isSupportedSeverity(event.severity)) {
        lastError_ = "runtime event severity must be debug, info, warn, or error";
        return std::nullopt;
    }

    sqlite3_stmt* statement = nullptr;
    constexpr const char* sql =
        "INSERT INTO runtime_events(category, severity, message, metadata_json) VALUES(?, ?, ?, ?)";
    if (sqlite3_prepare_v2(db_, sql, -1, &statement, nullptr) != SQLITE_OK) {
        lastError_ = sqlite3_errmsg(db_);
        return std::nullopt;
    }

    sqlite3_bind_text(statement, 1, event.category.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement, 2, event.severity.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement, 3, event.message.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement, 4, event.metadataJson.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(statement) != SQLITE_DONE) {
        lastError_ = sqlite3_errmsg(db_);
        sqlite3_finalize(statement);
        return std::nullopt;
    }
    const auto id = static_cast<int>(sqlite3_last_insert_rowid(db_));
    sqlite3_finalize(statement);

    sqlite3_stmt* selectStatement = nullptr;
    constexpr const char* selectSql = "SELECT id, category, severity, message, COALESCE(metadata_json, ''), created_at "
                                      "FROM runtime_events WHERE id = ?";
    if (sqlite3_prepare_v2(db_, selectSql, -1, &selectStatement, nullptr) != SQLITE_OK) {
        lastError_ = sqlite3_errmsg(db_);
        return std::nullopt;
    }
    sqlite3_bind_int(selectStatement, 1, id);
    std::optional<RuntimeEvent> result;
    if (sqlite3_step(selectStatement) == SQLITE_ROW) {
        result = RuntimeEvent{.id = sqlite3_column_int(selectStatement, 0),
                              .category = columnText(selectStatement, 1),
                              .severity = columnText(selectStatement, 2),
                              .message = columnText(selectStatement, 3),
                              .metadataJson = columnText(selectStatement, 4),
                              .createdAt = columnText(selectStatement, 5)};
    }
    sqlite3_finalize(selectStatement);
    if (result.has_value()) {
        lastError_.clear();
    }
    return result;
}

std::vector<RuntimeEvent> MetadataStore::listRuntimeEvents(ListOptions options) const {
    std::vector<RuntimeEvent> events;
    if (db_ == nullptr || options.limit == 0 || options.limit > 500 ||
        options.offset > static_cast<std::size_t>(std::numeric_limits<int>::max())) {
        return events;
    }

    sqlite3_stmt* statement = nullptr;
    constexpr const char* sql = "SELECT id, category, severity, message, COALESCE(metadata_json, ''), created_at FROM "
                                "runtime_events ORDER BY id LIMIT ? OFFSET ?";
    if (sqlite3_prepare_v2(db_, sql, -1, &statement, nullptr) != SQLITE_OK) {
        return events;
    }

    sqlite3_bind_int(statement, 1, static_cast<int>(options.limit));
    sqlite3_bind_int(statement, 2, static_cast<int>(options.offset));
    while (sqlite3_step(statement) == SQLITE_ROW) {
        events.push_back({.id = sqlite3_column_int(statement, 0),
                          .category = columnText(statement, 1),
                          .severity = columnText(statement, 2),
                          .message = columnText(statement, 3),
                          .metadataJson = columnText(statement, 4),
                          .createdAt = columnText(statement, 5)});
    }
    sqlite3_finalize(statement);
    return events;
}

bool MetadataStore::execute(const std::string& sql) {
    if (db_ == nullptr) {
        return false;
    }

    char* errorMessage = nullptr;
    if (sqlite3_exec(db_, sql.c_str(), nullptr, nullptr, &errorMessage) != SQLITE_OK) {
        lastError_ = errorMessage == nullptr ? sqlite3_errmsg(db_) : errorMessage;
        sqlite3_free(errorMessage);
        return false;
    }
    lastError_.clear();
    return true;
}

void MetadataStore::close() {
    if (db_ != nullptr) {
        sqlite3_close(db_);
        db_ = nullptr;
    }
}

} // namespace jsengine::storage
