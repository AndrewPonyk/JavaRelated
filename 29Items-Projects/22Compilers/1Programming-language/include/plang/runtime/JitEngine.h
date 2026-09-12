#pragma once

#include <string>
#include <variant>
#include <vector>

namespace plang::runtime {

using RuntimeValue = std::variant<std::monostate, bool, long long, double, std::string>;

struct EvaluationResult {
    RuntimeValue value;
    std::vector<std::string> diagnostics;
};

class JitEngine {
public:
    [[nodiscard]] EvaluationResult evaluate(std::string llvmIr) const;
};

std::string renderValue(const RuntimeValue& value);

} // namespace plang::runtime
