#pragma once

#include "jsengine/compiler/parser.h"
#include "jsengine/vm/bytecode.h"

#include <string>

namespace jsengine::compiler {

struct CompileResult {
    bool ok{false};
    vm::BytecodeModule module;
    std::string error;
};

class BytecodeCompiler {
  public:
    CompileResult compile(const AstNode& program) const;
};

} // namespace jsengine::compiler
