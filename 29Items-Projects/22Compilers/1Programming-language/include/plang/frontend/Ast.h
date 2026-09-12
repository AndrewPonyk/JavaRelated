#pragma once

#include <memory>
#include <optional>
#include <string>
#include <utility>
#include <variant>
#include <vector>

namespace plang::frontend {

struct SourceRange {
    std::size_t begin{0};
    std::size_t end{0};
};

struct Expr;
using ExprPtr = std::unique_ptr<Expr>;

struct NumberLiteral {
    double value{0.0};
    bool isInteger{false};
};

struct StringLiteral {
    std::string value;
};

struct BoolLiteral {
    bool value{false};
};

struct NullLiteral {};

struct Identifier {
    std::string name;
};

struct ArrayLiteral {
    std::vector<ExprPtr> elements;
};

struct RecordField {
    std::string name;
    ExprPtr value;
};

struct RecordLiteral {
    std::vector<RecordField> fields;
};

struct UnaryExpr {
    std::string op;
    ExprPtr operand;
};

struct BinaryExpr {
    std::string op;
    ExprPtr left;
    ExprPtr right;
};

struct CallExpr {
    ExprPtr callee;
    std::vector<ExprPtr> args;
};

struct IndexExpr {
    ExprPtr target;
    ExprPtr index;
};

struct MemberExpr {
    ExprPtr target;
    std::string member;
};

struct Expr {
    SourceRange range;
    std::variant<NumberLiteral,
                 StringLiteral,
                 BoolLiteral,
                 NullLiteral,
                 Identifier,
                 ArrayLiteral,
                 RecordLiteral,
                 UnaryExpr,
                 BinaryExpr,
                 CallExpr,
                 IndexExpr,
                 MemberExpr>
        node;
};

struct LetStatement {
    std::string name;
    ExprPtr initializer;
};

struct ExpressionStatement {
    ExprPtr expression;
};

struct Statement {
    std::variant<LetStatement, ExpressionStatement> node;
};

struct Module {
    std::vector<Statement> statements;
};

inline ExprPtr makeNumber(double value, SourceRange range = {}, bool isInteger = false) {
    auto expr = std::make_unique<Expr>();
    expr->range = range;
    expr->node = NumberLiteral{value, isInteger};
    return expr;
}

inline ExprPtr makeString(std::string value, SourceRange range = {}) {
    auto expr = std::make_unique<Expr>();
    expr->range = range;
    expr->node = StringLiteral{std::move(value)};
    return expr;
}

inline ExprPtr makeBool(bool value, SourceRange range = {}) {
    auto expr = std::make_unique<Expr>();
    expr->range = range;
    expr->node = BoolLiteral{value};
    return expr;
}

inline ExprPtr makeNull(SourceRange range = {}) {
    auto expr = std::make_unique<Expr>();
    expr->range = range;
    expr->node = NullLiteral{};
    return expr;
}

inline ExprPtr makeIdentifier(std::string name, SourceRange range = {}) {
    auto expr = std::make_unique<Expr>();
    expr->range = range;
    expr->node = Identifier{std::move(name)};
    return expr;
}

} // namespace plang::frontend
