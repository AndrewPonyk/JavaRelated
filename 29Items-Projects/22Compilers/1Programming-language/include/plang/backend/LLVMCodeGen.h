#pragma once

#include "plang/frontend/Ast.h"

#include <string>
#include <vector>

namespace plang::backend {

struct CodeGenDiagnostic {
    std::string message;
};

struct CodeGenResult {
    std::string llvmIr;
    std::vector<CodeGenDiagnostic> diagnostics;
};

class LLVMCodeGen {
public:
    [[nodiscard]] CodeGenResult emitModule(const frontend::Module& module) const;
};

} // namespace plang::backend
