#include "plang/runtime/JitEngine.h"

#include <cstdlib>
#include <sstream>
#include <string_view>

namespace plang::runtime {

namespace {

std::string metadataValue(const std::string& ir, std::string_view key) {
    const std::string prefix = "; plang." + std::string(key) + " = ";
    const std::size_t start = ir.find(prefix);
    if (start == std::string::npos) {
        return {};
    }
    const std::size_t valueStart = start + prefix.size();
    const std::size_t valueEnd = ir.find('\n', valueStart);
    return ir.substr(valueStart, valueEnd == std::string::npos ? std::string::npos
                                                               : valueEnd - valueStart);
}

std::string unescape(const std::string& text) {
    std::string out;
    for (std::size_t index = 0; index < text.size(); ++index) {
        if (text[index] != '\\' || index + 1 >= text.size()) {
            out.push_back(text[index]);
            continue;
        }
        const char escaped = text[++index];
        switch (escaped) {
        case 'n':
            out.push_back('\n');
            break;
        case 'r':
            out.push_back('\r');
            break;
        case 't':
            out.push_back('\t');
            break;
        case '\\':
        case '"':
            out.push_back(escaped);
            break;
        default:
            out.push_back(escaped);
            break;
        }
    }
    return out;
}

} // namespace

EvaluationResult JitEngine::evaluate(std::string llvmIr) const {
    if (llvmIr.empty()) {
        return EvaluationResult{std::monostate{}, {"Cannot evaluate empty LLVM IR."}};
    }

    const std::string kind = metadataValue(llvmIr, "result.kind");
    const std::string value = metadataValue(llvmIr, "result.value");
    if (kind.empty()) {
        return EvaluationResult{std::monostate{}, {"IR does not contain plang result metadata."}};
    }
    if (kind == "unit") {
        return EvaluationResult{std::monostate{}, {}};
    }
    if (kind == "bool") {
        return EvaluationResult{value == "true", {}};
    }
    if (kind == "int") {
        return EvaluationResult{std::strtoll(value.c_str(), nullptr, 10), {}};
    }
    if (kind == "float") {
        return EvaluationResult{std::strtod(value.c_str(), nullptr), {}};
    }
    if (kind == "string") {
        return EvaluationResult{unescape(value), {}};
    }
    return EvaluationResult{std::monostate{}, {"Unsupported IR result kind: " + kind}};
}

std::string renderValue(const RuntimeValue& value) {
    if (std::holds_alternative<std::monostate>(value)) {
        return "<unit>";
    }
    if (std::holds_alternative<bool>(value)) {
        return std::get<bool>(value) ? "true" : "false";
    }
    if (std::holds_alternative<long long>(value)) {
        return std::to_string(std::get<long long>(value));
    }
    if (std::holds_alternative<double>(value)) {
        std::ostringstream out;
        out << std::get<double>(value);
        return out.str();
    }
    return std::get<std::string>(value);
}

} // namespace plang::runtime
