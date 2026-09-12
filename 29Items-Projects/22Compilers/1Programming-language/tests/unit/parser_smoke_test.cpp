#include "plang/frontend/Lexer.h"
#include "plang/frontend/Parser.h"
#include "plang/frontend/TypeInferencer.h"

#include <cassert>
#include <variant>

int main() {
    plang::frontend::Lexer lexer(
        "let answer = 40 + 2 * 3; let xs = [answer, 50]; {total: xs[0], ok: true}.total;");
    plang::frontend::Parser parser(lexer.tokenize());
    auto result = parser.parseModule();

    assert(result.diagnostics.empty());
    assert(result.module.statements.size() == 3);

    plang::frontend::TypeInferencer inferencer;
    auto types = inferencer.infer(result.module);
    assert(types.diagnostics.empty());
    assert(types.symbols.at("answer") == plang::frontend::TypeKind::Int);
    assert(types.symbols.at("xs") == plang::frontend::TypeKind::Array);
    return 0;
}
