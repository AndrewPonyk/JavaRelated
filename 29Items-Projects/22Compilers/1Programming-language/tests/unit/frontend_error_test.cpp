#include "plang/frontend/Lexer.h"
#include "plang/frontend/Parser.h"
#include "plang/frontend/TypeInferencer.h"

#include <cassert>

int main() {
    plang::frontend::Lexer lexer("let x = \"text\" - 1; missing;");
    plang::frontend::Parser parser(lexer.tokenize());
    auto parsed = parser.parseModule();
    assert(parsed.diagnostics.empty());

    plang::frontend::TypeInferencer inferencer;
    auto checked = inferencer.infer(parsed.module);
    assert(checked.diagnostics.size() == 2);
    return 0;
}
