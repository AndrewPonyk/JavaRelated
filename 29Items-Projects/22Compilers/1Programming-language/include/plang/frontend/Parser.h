#pragma once

#include "plang/frontend/Ast.h"
#include "plang/frontend/Lexer.h"

#include <string>
#include <vector>

namespace plang::frontend {

struct ParseDiagnostic {
    std::string message;
    std::size_t offset{0};
};

struct ParseResult {
    Module module;
    std::vector<ParseDiagnostic> diagnostics;
};

class Parser {
public:
    explicit Parser(std::vector<Token> tokens);

    [[nodiscard]] ParseResult parseModule();

private:
    [[nodiscard]] bool isAtEnd() const;
    [[nodiscard]] const Token& peek() const;
    [[nodiscard]] const Token& previous() const;
    [[nodiscard]] bool check(TokenKind kind) const;
    bool match(TokenKind kind);
    Token consume(TokenKind kind, std::string message);

    Statement parseStatement();
    Statement parseLetStatement();
    ExprPtr parseExpression(int minBindingPower = 0);
    ExprPtr parsePrefix();
    ExprPtr parsePostfix(ExprPtr expr);
    ExprPtr parsePrimary();
    ExprPtr parseArrayLiteral(const Token& leftBracket);
    ExprPtr parseRecordLiteral(const Token& leftBrace);
    [[nodiscard]] int infixBindingPower(TokenKind kind) const;
    void synchronize();
    void diagnostic(std::string message, std::size_t offset);

    std::vector<Token> tokens_;
    std::size_t current_{0};
    std::vector<ParseDiagnostic> diagnostics_;
};

} // namespace plang::frontend
