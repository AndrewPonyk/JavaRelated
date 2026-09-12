#pragma once

#include <cstddef>
#include <string>
#include <vector>

namespace jsengine::vm {

struct TraceRecord {
    std::size_t loopHeaderOffset{0};
    std::vector<std::string> guards;
};

class TraceRecorder {
  public:
    void recordLoopHit(std::size_t bytecodeOffset);
    bool isHot(std::size_t bytecodeOffset) const;
    TraceRecord startTrace(std::size_t bytecodeOffset) const;

  private:
    static constexpr std::size_t HotThreshold = 100;
    std::size_t lastOffset_{0};
    std::size_t hitCount_{0};
};

} // namespace jsengine::vm
