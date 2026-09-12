#pragma once

#include "engine/core/Result.hpp"
#include "engine/core/Types.hpp"

#include <atomic>
#include <memory>
#include <string>
#include <unordered_map>
#include <vector>

/// @file AssetManager.hpp
/// @brief Reference-counted, async, de-duplicating asset cache.
///
/// Assets are loaded off the main thread via the job system and cached by path so a
/// second request returns the existing handle. The AssetDatabase records imported
/// asset metadata (guids, hashes, dependencies) for fast lookup and reuse.

namespace engine::jobs {
class JobSystem;
}

namespace engine::resources {

enum class AssetType : u8 { Unknown, Mesh, Texture, Shader, Audio, Scene, TerrainModel };
enum class LoadState : u8 { Idle, Queued, Loading, Ready, Failed };

/// Opaque, type-tagged asset handle.
struct AssetHandle {
    u32       id   = 0xFFFF'FFFFu;
    AssetType type = AssetType::Unknown;
    [[nodiscard]] bool valid() const noexcept { return id != 0xFFFF'FFFFu; }
};

class AssetDatabase; // forward

class AssetManager {
public:
    explicit AssetManager(jobs::JobSystem& jobs, AssetDatabase* db = nullptr)
        : jobs_(&jobs), db_(db) {}

    /// Request an asset by path. Returns an existing handle if already requested.
    /// Loading happens asynchronously; poll loadState() for readiness.
    [[nodiscard]] AssetHandle load(const std::string& path, AssetType type);

    [[nodiscard]] LoadState loadState(AssetHandle handle) const;

    /// Bytes read for a Ready asset (0 if not loaded).
    [[nodiscard]] usize loadedSize(AssetHandle handle) const;

    /// Decrement refcount; unloads when it reaches zero.
    void release(AssetHandle handle);

    /// Block until everything in flight finishes (e.g., at a loading screen).
    void waitAll();

private:
    struct Entry {
        std::string            path;
        AssetType              type = AssetType::Unknown;
        std::atomic<LoadState> state{LoadState::Idle};
        std::atomic<usize>     sizeBytes{0};
        u32                    refCount = 0;
    };

    jobs::JobSystem*                                    jobs_ = nullptr;
    AssetDatabase*                                      db_   = nullptr;
    std::unordered_map<std::string, u32>                byPath_;
    std::vector<std::unique_ptr<Entry>>                 entries_;
};

} // namespace engine::resources
