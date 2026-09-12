#pragma once

#include <string>

namespace jsengine {

struct EvaluationResult {
    bool ok{false};
    std::string value;
    std::string error;
};

class Engine {
  public:
    EvaluationResult evaluate(const std::string& source);
};

} // namespace jsengine
