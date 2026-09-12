#include "jsengine/compiler/bytecode_compiler.h"
#include "jsengine/compiler/lexer.h"
#include "jsengine/compiler/parser.h"

#include <cassert>
#include <iostream>

namespace {

jsengine::compiler::ParseResult parse(const std::string& source) {
    jsengine::compiler::Lexer lexer;
    jsengine::compiler::Parser parser;
    return parser.parseProgram(lexer.tokenize(source));
}

void coversParserEdges() {
    jsengine::compiler::Parser parser;
    assert(!parser.parseProgram({}).ok);

    const auto invalid = parse("let x = 1.;");
    assert(!invalid.ok);

    const auto constWithoutInitializer = parse("const x;");
    assert(!constWithoutInitializer.ok);

    const auto missingParen = parse("(1 + 2;");
    assert(!missingParen.ok);

    const auto missingProperty = parse("let x = {}; x.;");
    assert(!missingProperty.ok);

    const auto badObject = parse("let x = { : 1 };");
    assert(!badObject.ok);

    const auto valid = parse("var x; x = -1; return x;");
    assert(valid.ok);
    assert(valid.nodes.front().children.size() == 3);
}

void coversCompilerEdges() {
    jsengine::compiler::BytecodeCompiler compiler;

    const auto wrongRoot =
        compiler.compile({.kind = jsengine::compiler::AstKind::Literal, .text = "1", .children = {}});
    assert(!wrongRoot.ok);

    jsengine::compiler::AstNode unsupportedStatement{
        .kind = jsengine::compiler::AstKind::Program,
        .text = "Program",
        .children = {{.kind = jsengine::compiler::AstKind::Property, .text = "bad", .children = {}}}};
    assert(!compiler.compile(unsupportedStatement).ok);

    const auto parsed = parse("return { value: 1 + 2 }.value;");
    assert(parsed.ok);
    assert(compiler.compile(parsed.nodes.front()).ok);
}

} // namespace

int main() {
    coversParserEdges();
    coversCompilerEdges();
    std::cout << "compiler/parser tests passed\n";
    return 0;
}
