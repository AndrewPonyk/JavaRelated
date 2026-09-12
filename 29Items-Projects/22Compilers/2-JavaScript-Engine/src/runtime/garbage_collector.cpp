#include "jsengine/runtime/garbage_collector.h"

namespace jsengine::runtime {

void GarbageCollector::recordAllocation(std::size_t bytes) {
    stats_.bytesAllocated += bytes;
    stats_.nurseryBytes += bytes;
}

void GarbageCollector::writeBarrier() {
    ++rememberedSetEntries_;
}

void GarbageCollector::collectNursery() {
    stats_.oldGenerationBytes += stats_.nurseryBytes;
    stats_.nurseryBytes = 0;
    rememberedSetEntries_ = 0;
    ++stats_.nurseryCollections;
}

void GarbageCollector::startIncrementalMarking() {
    stats_.incrementalMarking = true;
    ++stats_.majorCollections;
}

void GarbageCollector::finishIncrementalMarking() {
    stats_.incrementalMarking = false;
}

GcStats GarbageCollector::stats() const {
    return stats_;
}

} // namespace jsengine::runtime
