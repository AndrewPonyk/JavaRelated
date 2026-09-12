#include "jsengine/engine.h"

#include "jsengine/compiler/bytecode_compiler.h"
#include "jsengine/compiler/lexer.h"
#include "jsengine/compiler/parser.h"
#include "jsengine/vm/virtual_machine.h"

namespace jsengine {

EvaluationResult Engine::evaluate(const std::string& source) {
    compiler::Lexer lexer;
    const auto tokens = lexer.tokenize(source);

    compiler::Parser parser;
    const auto parsed = parser.parseProgram(tokens);
    if (!parsed.ok) {
        return {.ok = false, .value = "", .error = parsed.error};
    }

    if (parsed.nodes.empty()) {
        return {.ok = false, .value = "", .error = "Parser produced no program node"};
    }

    compiler::BytecodeCompiler compiler;
    const auto compiled = compiler.compile(parsed.nodes.front());
    if (!compiled.ok) {
        return {.ok = false, .value = "", .error = compiled.error};
    }

    vm::VirtualMachine vm;
    const auto executed = vm.execute(compiled.module);
    return {.ok = executed.ok, .value = executed.value.toString(), .error = executed.error};
}

} // namespace jsengine
