#pragma once

#include "jsengine/compiler/lexer.h"

#include <memory>
#include <string>
#include <vector>

namespace jsengine::compiler {

enum class AstKind {
    Program,
    VariableDeclaration,
    ReturnStatement,
    ExpressionStatement,
    AssignmentExpression,
    PropertyAssignmentExpression,
    BinaryExpression,
    UnaryExpression,
    Literal,
    Identifier,
    ObjectLiteral,
    Property,
    PropertyAccess
};

struct AstNode {
    AstKind kind{AstKind::Program};
    std::string text;
    TokenType literalType{TokenType::Invalid};
    std::size_t line{1};
    std::size_t column{1};
    std::vector<AstNode> children;
    bool constant{false};
};

struct ParseResult {
    bool ok{false};
    std::vector<AstNode> nodes;
    std::string error;
};

class Parser {
  public:
    ParseResult parseProgram(const std::vector<Token>& tokens) const;
};

} // namespace jsengine::compiler
