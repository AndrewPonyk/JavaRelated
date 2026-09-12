#pragma once

#include "jsengine/vm/bytecode.h"
#include "jsengine/vm/inline_cache.h"

#include <cstddef>
#include <string>
#include <unordered_map>
#include <vector>

namespace jsengine::vm {

struct ExecutionResult {
    bool ok{false};
    runtime::Value value;
    std::string error;
};

class VirtualMachine {
  public:
    ExecutionResult execute(const BytecodeModule& module);

  private:
    struct GlobalBinding {
        runtime::Value value;
        bool mutableBinding{true};
    };

    ExecutionResult declareName(const BytecodeModule& module, const Instruction& instruction, bool mutableBinding);
    ExecutionResult storeName(const BytecodeModule& module, const Instruction& instruction);
    bool pop(runtime::Value& value, std::string& error);
    std::vector<runtime::Value> stack_;
    std::unordered_map<std::string, GlobalBinding> globals_;
    std::unordered_map<std::size_t, PropertyInlineCache> propertyCaches_;
};

} // namespace jsengine::vm
