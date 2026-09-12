#include "plang/backend/LLVMCodeGen.h"
#include "plang/frontend/Lexer.h"
#include "plang/frontend/Parser.h"
#include "plang/frontend/SourcePreview.h"
#include "plang/frontend/TypeInferencer.h"
#include "plang/runtime/JitEngine.h"

#include <iostream>
#include <string>

namespace {

int compileAndRun(std::string source) {
    plang::frontend::Lexer lexer(source);
    plang::frontend::Parser parser(lexer.tokenize());
    auto parseResult = parser.parseModule();

    for (const auto& diagnostic : parseResult.diagnostics) {
        std::cerr << "parse error at " << diagnostic.offset << ": " << diagnostic.message << '\n';
    }
    if (!parseResult.diagnostics.empty()) {
        return 1;
    }

    plang::frontend::TypeInferencer inferencer;
    auto typeResult = inferencer.infer(parseResult.module);
    for (const auto& diagnostic : typeResult.diagnostics) {
        std::cerr << "type warning: " << diagnostic.message << '\n';
    }

    plang::backend::LLVMCodeGen codegen;
    auto codegenResult = codegen.emitModule(parseResult.module);
    for (const auto& diagnostic : codegenResult.diagnostics) {
        std::cerr << "codegen error: " << diagnostic.message << '\n';
    }

    plang::runtime::JitEngine jit;
    auto eval = jit.evaluate(codegenResult.llvmIr);
    for (const auto& diagnostic : eval.diagnostics) {
        std::cerr << "jit error: " << diagnostic << '\n';
    }

    std::cout << plang::runtime::renderValue(eval.value) << '\n';
    return eval.diagnostics.empty() ? 0 : 1;
}

} // namespace

int main(int argc, char** argv) {
    if (argc > 1 && std::string(argv[1]) == "--version") {
        std::cout << "plang 0.1.0\n";
        return 0;
    }
    if (argc > 1 && (std::string(argv[1]) == "--help" || std::string(argv[1]) == "-h")) {
        std::cout << "usage: plang [--version] [source-file]\n";
        return 0;
    }

    if (argc > 1) {
        plang::frontend::SourcePreview sourcePreview;
        auto state = sourcePreview.fetchFromFile(argv[1]);
        std::cout << sourcePreview.render(state) << '\n';
        if (const auto* ready = std::get_if<plang::frontend::SourceReady>(&state)) {
            return compileAndRun(ready->contents);
        }
        return 1;
    }

    std::cout << "plang repl. Enter one complete statement per line; Ctrl+C to exit.\n";
    std::string line;
    while (std::getline(std::cin, line)) {
        if (!line.empty()) {
            (void)compileAndRun(line);
        }
    }
    return 0;
}
