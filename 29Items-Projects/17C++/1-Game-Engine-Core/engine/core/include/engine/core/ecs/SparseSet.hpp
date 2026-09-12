#pragma once

#include "engine/core/Assert.hpp"
#include "engine/core/Types.hpp"
#include "engine/core/ecs/Entity.hpp"

#include <vector>

/// @file SparseSet.hpp
/// @brief Entity membership + stable dense ordering.
///
/// The sparse array maps an entity *index* -> a position in the dense array; the
/// dense array is a packed, contiguous list of member entities. This is what makes
/// component iteration cache-friendly: walking `dense_` (and the parallel component
/// array in ComponentPool) is a linear memory scan. O(1) insert/remove/contains.

namespace engine::ecs {

class SparseSet {
public:
    static constexpr u32 kTombstone = 0xFFFF'FFFFu;

    [[nodiscard]] bool contains(Entity e) const noexcept {
        const u32 idx = e.index();
        return idx < sparse_.size() && sparse_[idx] != kTombstone
               && dense_[sparse_[idx]] == e;
    }

    /// Insert an entity; returns its dense position. Caller must ensure !contains(e).
    u32 insert(Entity e) {
        ENGINE_ASSERT(!contains(e), "double insert of entity {}", e.id);
        const u32 idx = e.index();
        if (idx >= sparse_.size()) {
            sparse_.resize(idx + 1, kTombstone);
        }
        const auto pos = static_cast<u32>(dense_.size());
        sparse_[idx]   = pos;
        dense_.push_back(e);
        return pos;
    }

    /// Swap-and-pop removal. Returns the dense position that was vacated so a
    /// parallel component array can mirror the same swap.
    u32 remove(Entity e) {
        ENGINE_ASSERT(contains(e), "removing absent entity {}", e.id);
        const u32 removedPos = sparse_[e.index()];
        const u32 lastPos    = static_cast<u32>(dense_.size()) - 1;
        const Entity moved   = dense_[lastPos];

        dense_[removedPos]      = moved;
        sparse_[moved.index()]  = removedPos;
        sparse_[e.index()]      = kTombstone;
        dense_.pop_back();
        return removedPos;
    }

    [[nodiscard]] u32 denseIndex(Entity e) const noexcept {
        ENGINE_ASSERT(contains(e), "denseIndex of absent entity {}", e.id);
        return sparse_[e.index()];
    }

    [[nodiscard]] usize size() const noexcept { return dense_.size(); }
    [[nodiscard]] bool  empty() const noexcept { return dense_.empty(); }
    [[nodiscard]] const std::vector<Entity>& entities() const noexcept { return dense_; }

    void clear() noexcept {
        sparse_.clear();
        dense_.clear();
    }

private:
    std::vector<u32>    sparse_; // entity index -> dense position (or kTombstone)
    std::vector<Entity> dense_;  // packed list of member entities
};

} // namespace engine::ecs
