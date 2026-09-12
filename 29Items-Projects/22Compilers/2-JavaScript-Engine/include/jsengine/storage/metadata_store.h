#pragma once

#include <cstddef>
#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

struct sqlite3;

namespace jsengine::storage {

struct ListOptions {
    std::size_t limit{100};
    std::size_t offset{0};
};

struct ModuleCacheEntry {
    int id{0};
    std::string specifier;
    std::string resolvedPath;
    std::string format;
    std::string bytecodeHash;
};

struct RuntimeEvent {
    int id{0};
    std::string category;
    std::string severity;
    std::string message;
    std::string metadataJson;
    std::string createdAt;
};

class MetadataStore {
  public:
    MetadataStore();
    explicit MetadataStore(std::string databasePath);
    ~MetadataStore();

    MetadataStore(const MetadataStore&) = delete;
    MetadataStore& operator=(const MetadataStore&) = delete;
    MetadataStore(MetadataStore&& other) noexcept;
    MetadataStore& operator=(MetadataStore&& other) noexcept;

    bool open(const std::string& databasePath);
    bool initializeSchema();
    bool isOpen() const;
    const std::string& lastError() const;

    void put(std::string key, std::string value);
    std::optional<std::string> get(const std::string& key) const;
    bool remove(const std::string& key);

    std::optional<ModuleCacheEntry> createModuleCacheEntry(ModuleCacheEntry entry);
    std::vector<ModuleCacheEntry> listModuleCacheEntries(ListOptions options = {}) const;
    std::optional<ModuleCacheEntry> getModuleCacheEntry(int id) const;
    bool deleteModuleCacheEntry(int id);

    std::optional<RuntimeEvent> recordRuntimeEvent(RuntimeEvent event);
    std::vector<RuntimeEvent> listRuntimeEvents(ListOptions options = {}) const;

  private:
    bool execute(const std::string& sql);
    void close();

    sqlite3* db_{nullptr};
    std::unordered_map<std::string, std::string> memoryValues_;
    std::string lastError_;
};

} // namespace jsengine::storage
