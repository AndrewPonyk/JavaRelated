#include "jsengine/vm/inline_cache.h"

#include <utility>

namespace jsengine::vm {

void PropertyInlineCache::recordHit(std::string shapeId, std::size_t slot) {
    if (state_ == InlineCacheState::Empty) {
        shapeId_ = std::move(shapeId);
        slot_ = slot;
        state_ = InlineCacheState::Monomorphic;
        return;
    }

    if (state_ == InlineCacheState::Monomorphic && shapeId_ != shapeId) {
        state_ = InlineCacheState::Polymorphic;
        return;
    }

    if (state_ == InlineCacheState::Polymorphic && shapeId_ != shapeId) {
        state_ = InlineCacheState::Megamorphic;
    }
}

InlineCacheState PropertyInlineCache::state() const {
    return state_;
}

std::size_t PropertyInlineCache::cachedSlot() const {
    return slot_;
}

} // namespace jsengine::vm
