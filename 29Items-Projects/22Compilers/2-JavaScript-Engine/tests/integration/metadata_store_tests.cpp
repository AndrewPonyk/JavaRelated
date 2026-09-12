#include "jsengine/storage/metadata_store.h"

#include <cassert>
#include <filesystem>
#include <iostream>

int main() {
    const auto databasePath = std::filesystem::temp_directory_path() / "jsengine_metadata_store_tests.sqlite";
    std::filesystem::remove(databasePath);

    jsengine::storage::MetadataStore store(databasePath.string());
    assert(store.isOpen());

    store.put("log.level", "debug");
    assert(store.get("log.level").value() == "debug");
    assert(store.remove("log.level"));
    assert(!store.get("log.level").has_value());
    store.put(" ", "ignored");
    assert(!store.lastError().empty());

    const auto entry = store.createModuleCacheEntry(
        {.specifier = "./main.js", .resolvedPath = "/app/main.js", .format = "esm", .bytecodeHash = "abc123"});
    assert(entry.has_value());
    assert(store.getModuleCacheEntry(entry->id).has_value());
    assert(store.listModuleCacheEntries().size() == 1);
    assert(store.listModuleCacheEntries({.limit = 0, .offset = 0}).empty());
    assert(!store
                .createModuleCacheEntry(
                    {.id = 0, .specifier = "", .resolvedPath = "/bad.js", .format = "esm", .bytecodeHash = ""})
                .has_value());
    assert(!store
                .createModuleCacheEntry(
                    {.id = 0, .specifier = "./bad.js", .resolvedPath = "/bad.js", .format = "amd", .bytecodeHash = ""})
                .has_value());
    assert(store.deleteModuleCacheEntry(entry->id));

    const auto event = store.recordRuntimeEvent(
        {.id = 0, .category = "vm", .severity = "info", .message = "started", .metadataJson = "{}", .createdAt = ""});
    assert(event.has_value());
    assert(store.listRuntimeEvents().size() == 1);
    assert(store.listRuntimeEvents({.limit = 0, .offset = 0}).empty());
    assert(!store
                .recordRuntimeEvent({.id = 0,
                                     .category = "vm",
                                     .severity = "fatal",
                                     .message = "bad",
                                     .metadataJson = "",
                                     .createdAt = ""})
                .has_value());

    std::filesystem::remove(databasePath);
    std::cout << "metadata store tests passed\n";
    return 0;
}
