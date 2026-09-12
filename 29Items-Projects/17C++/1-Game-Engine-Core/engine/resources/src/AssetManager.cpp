#include "engine/resources/AssetManager.hpp"

#include "engine/core/Log.hpp"
#include "engine/core/jobs/JobSystem.hpp"
#include "engine/platform/Filesystem.hpp"

/// @file AssetManager.cpp
/// @brief Async, de-duplicating asset cache. Loads run on the job system; repeated
/// requests for the same path share one entry and bump its refcount.

namespace engine::resources {

AssetHandle AssetManager::load(const std::string& path, AssetType type) {
    if (const auto it = byPath_.find(path); it != byPath_.end()) {
        entries_[it->second]->refCount++;
        return AssetHandle{it->second, type};
    }

    auto entry  = std::make_unique<Entry>();
    entry->path = path;
    entry->type = type;
    entry->refCount = 1;
    entry->state.store(LoadState::Queued);

    const auto id = static_cast<u32>(entries_.size());
    byPath_.emplace(path, id);
    entries_.push_back(std::move(entry));
    Entry* rec = entries_.back().get();

    jobs_->dispatch([rec] {
        rec->state.store(LoadState::Loading);
        // Real I/O: read the asset bytes off the main thread. Typed decoding
        // (mesh/texture/...) is a per-format extension layered on these bytes.
        auto bytes = platform::fs::readBytes(platform::fs::resolveAsset(rec->path));
        if (!bytes) {
            rec->state.store(LoadState::Failed);
            return;
        }
        rec->sizeBytes.store(bytes.value().size());
        rec->state.store(LoadState::Ready);
    });

    log::debug("[Assets] queued '{}' (type {})", path, static_cast<int>(type));
    return AssetHandle{id, type};
}

usize AssetManager::loadedSize(AssetHandle handle) const {
    if (!handle.valid() || handle.id >= entries_.size()) {
        return 0;
    }
    return entries_[handle.id]->sizeBytes.load();
}

LoadState AssetManager::loadState(AssetHandle handle) const {
    if (!handle.valid() || handle.id >= entries_.size()) {
        return LoadState::Failed;
    }
    return entries_[handle.id]->state.load();
}

void AssetManager::release(AssetHandle handle) {
    if (!handle.valid() || handle.id >= entries_.size()) {
        return;
    }
    Entry& e = *entries_[handle.id];
    if (e.refCount > 0 && --e.refCount == 0) {
        // Refcount hit zero: mark unloaded and reset size. The slot is retained so a
        // later load of the same path reuses it (and GPU resources, once decoders exist).
        e.sizeBytes.store(0);
        e.state.store(LoadState::Idle);
    }
}

void AssetManager::waitAll() {
    if (jobs_ != nullptr) {
        jobs_->waitForIdle();
    }
}

} // namespace engine::resources
