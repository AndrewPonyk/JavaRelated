#pragma once

#include <cstddef>
#include <string>

namespace jsengine::vm {

enum class InlineCacheState { Empty, Monomorphic, Polymorphic, Megamorphic };

class PropertyInlineCache {
  public:
    void recordHit(std::string shapeId, std::size_t slot);
    InlineCacheState state() const;
    std::size_t cachedSlot() const;

  private:
    InlineCacheState state_{InlineCacheState::Empty};
    std::string shapeId_;
    std::size_t slot_{0};
};

} // namespace jsengine::vm
