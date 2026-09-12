#pragma once

#include "jsengine/runtime/value.h"

#include <cstdint>
#include <string>
#include <vector>

namespace jsengine::vm {

enum class Opcode : std::uint8_t {
    LoadConst,
    LoadName,
    DeclareName,
    DeclareConst,
    StoreName,
    Add,
    Subtract,
    Multiply,
    Divide,
    Negate,
    MakeObject,
    DefineProperty,
    GetProperty,
    SetProperty,
    Pop,
    Return,
    Nop
};

struct Instruction {
    Opcode opcode{Opcode::Nop};
    std::uint32_t operand{0};
};

class BytecodeModule {
  public:
    std::uint32_t addConstant(runtime::Value value);
    std::uint32_t addName(std::string name);
    void emit(Opcode opcode, std::uint32_t operand = 0);

    const std::vector<runtime::Value>& constants() const;
    const std::vector<std::string>& names() const;
    const std::vector<Instruction>& instructions() const;

  private:
    std::vector<runtime::Value> constants_;
    std::vector<std::string> names_;
    std::vector<Instruction> instructions_;
};

} // namespace jsengine::vm
