#pragma once

#include <cstddef>
#include <string>

namespace jsengine::runtime {

struct GcStats {
    std::size_t nurseryCollections{0};
    std::size_t majorCollections{0};
    std::size_t bytesAllocated{0};
    std::size_t nurseryBytes{0};
    std::size_t oldGenerationBytes{0};
    bool incrementalMarking{false};
};

class GarbageCollector {
  public:
    void recordAllocation(std::size_t bytes);
    void writeBarrier();
    void collectNursery();
    void finishIncrementalMarking();
    void startIncrementalMarking();
    GcStats stats() const;

  private:
    GcStats stats_;
    std::size_t rememberedSetEntries_{0};
};

} // namespace jsengine::runtime
