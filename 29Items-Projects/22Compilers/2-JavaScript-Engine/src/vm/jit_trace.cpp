#include "jsengine/vm/jit_trace.h"

namespace jsengine::vm {

void TraceRecorder::recordLoopHit(std::size_t bytecodeOffset) {
    if (lastOffset_ == bytecodeOffset) {
        ++hitCount_;
    } else {
        lastOffset_ = bytecodeOffset;
        hitCount_ = 1;
    }
}

bool TraceRecorder::isHot(std::size_t bytecodeOffset) const {
    return lastOffset_ == bytecodeOffset && hitCount_ >= HotThreshold;
}

TraceRecord TraceRecorder::startTrace(std::size_t bytecodeOffset) const {
    TraceRecord record{.loopHeaderOffset = bytecodeOffset, .guards = {}};
    if (isHot(bytecodeOffset)) {
        record.guards = {"shape-stability", "numeric-arithmetic", "interpreter-state-map"};
    }
    return record;
}

} // namespace jsengine::vm
