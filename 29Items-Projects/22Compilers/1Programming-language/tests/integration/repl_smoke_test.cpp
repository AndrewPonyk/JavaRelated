#include "plang/backend/LLVMCodeGen.h"
#include "plang/frontend/Lexer.h"
#include "plang/frontend/Parser.h"
#include "plang/runtime/JitEngine.h"

#include <cassert>
#include <string>
#include <variant>

int main() {
    plang::frontend::Lexer lexer(
        "let answer = 40 + 2; let label = \"answer=\" + \"42\"; len([answer, 7, 9]);");
    plang::frontend::Parser parser(lexer.tokenize());
    auto parseResult = parser.parseModule();
    assert(parseResult.diagnostics.empty());

    plang::backend::LLVMCodeGen codegen;
    auto codegenResult = codegen.emitModule(parseResult.module);
    assert(!codegenResult.llvmIr.empty());
    assert(codegenResult.diagnostics.empty());

    plang::runtime::JitEngine jit;
    auto eval = jit.evaluate(codegenResult.llvmIr);
    assert(eval.diagnostics.empty());
    assert(std::holds_alternative<long long>(eval.value));
    assert(std::get<long long>(eval.value) == 3);
    return 0;
}
