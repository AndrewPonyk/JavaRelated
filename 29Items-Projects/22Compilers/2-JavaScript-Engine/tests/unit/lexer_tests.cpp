#include "jsengine/compiler/lexer.h"

#include <cassert>
#include <iostream>

int main() {
    jsengine::compiler::Lexer lexer;
    const auto tokens = lexer.tokenize("let answer = 42;");

    assert(tokens.size() == 6);
    assert(tokens[0].type == jsengine::compiler::TokenType::KeywordLet);
    assert(tokens[1].type == jsengine::compiler::TokenType::Identifier);
    assert(tokens[3].type == jsengine::compiler::TokenType::Number);
    assert(tokens.back().type == jsengine::compiler::TokenType::EndOfFile);

    const auto withComments = lexer.tokenize("// ignore\nconst ok = true;");
    assert(withComments[0].type == jsengine::compiler::TokenType::KeywordConst);
    assert(withComments[3].type == jsengine::compiler::TokenType::KeywordTrue);

    const auto decimals = lexer.tokenize("let n = 1.25;");
    assert(decimals[3].type == jsengine::compiler::TokenType::Number);
    assert(decimals[3].lexeme == "1.25");

    const auto badString = lexer.tokenize("'unterminated\n");
    assert(badString[0].type == jsengine::compiler::TokenType::Invalid);

    std::cout << "lexer tests passed\n";
    return 0;
}
