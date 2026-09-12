#include "jsengine/vm/inline_cache.h"
#include "jsengine/vm/jit_trace.h"

#include <cassert>
#include <iostream>

int main() {
    jsengine::vm::PropertyInlineCache cache;
    assert(cache.state() == jsengine::vm::InlineCacheState::Empty);
    cache.recordHit("shape-a", 3);
    assert(cache.state() == jsengine::vm::InlineCacheState::Monomorphic);
    assert(cache.cachedSlot() == 3);
    cache.recordHit("shape-b", 4);
    assert(cache.state() == jsengine::vm::InlineCacheState::Polymorphic);
    cache.recordHit("shape-c", 5);
    assert(cache.state() == jsengine::vm::InlineCacheState::Megamorphic);

    jsengine::vm::TraceRecorder recorder;
    assert(!recorder.isHot(10));
    auto cold = recorder.startTrace(10);
    assert(cold.guards.empty());
    for (int i = 0; i < 100; ++i) {
        recorder.recordLoopHit(10);
    }
    assert(recorder.isHot(10));
    auto hot = recorder.startTrace(10);
    assert(hot.loopHeaderOffset == 10);
    assert(!hot.guards.empty());
    recorder.recordLoopHit(11);
    assert(!recorder.isHot(10));

    std::cout << "cache and trace tests passed\n";
    return 0;
}
