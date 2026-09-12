#include "jsengine/vm/bytecode.h"

#include <utility>

namespace jsengine::vm {

std::uint32_t BytecodeModule::addConstant(runtime::Value value) {
    constants_.push_back(std::move(value));
    return static_cast<std::uint32_t>(constants_.size() - 1);
}

std::uint32_t BytecodeModule::addName(std::string name) {
    names_.push_back(std::move(name));
    return static_cast<std::uint32_t>(names_.size() - 1);
}

void BytecodeModule::emit(Opcode opcode, std::uint32_t operand) {
    instructions_.push_back({opcode, operand});
}

const std::vector<runtime::Value>& BytecodeModule::constants() const {
    return constants_;
}

const std::vector<std::string>& BytecodeModule::names() const {
    return names_;
}

const std::vector<Instruction>& BytecodeModule::instructions() const {
    return instructions_;
}

} // namespace jsengine::vm
