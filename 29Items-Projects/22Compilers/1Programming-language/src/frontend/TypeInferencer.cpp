#include "plang/frontend/TypeInferencer.h"

#include <variant>

namespace plang::frontend {

TypeCheckResult TypeInferencer::infer(const Module& module) const {
    TypeCheckResult result;
    for (const Statement& statement : module.statements) {
        if (const auto* let = std::get_if<LetStatement>(&statement.node)) {
            result.symbols[let->name] = inferExpression(*let->initializer, result);
        } else if (const auto* expression = std::get_if<ExpressionStatement>(&statement.node)) {
            (void)inferExpression(*expression->expression, result);
        }
    }
    return result;
}

TypeKind TypeInferencer::inferExpression(const Expr& expr, TypeCheckResult& result) const {
    if (const auto* number = std::get_if<NumberLiteral>(&expr.node)) {
        return number->isInteger ? TypeKind::Int : TypeKind::Float;
    }
    if (std::holds_alternative<StringLiteral>(expr.node)) {
        return TypeKind::String;
    }
    if (std::holds_alternative<BoolLiteral>(expr.node)) {
        return TypeKind::Bool;
    }
    if (std::holds_alternative<NullLiteral>(expr.node)) {
        return TypeKind::Null;
    }
    if (const auto* identifier = std::get_if<Identifier>(&expr.node)) {
        if (identifier->name == "len" || identifier->name == "print") {
            return TypeKind::Function;
        }
        const auto found = result.symbols.find(identifier->name);
        if (found == result.symbols.end()) {
            result.diagnostics.push_back(
                TypeDiagnostic{"Unknown symbol '" + identifier->name + "'.", expr.range});
            return TypeKind::Dynamic;
        }
        return found->second;
    }
    if (const auto* array = std::get_if<ArrayLiteral>(&expr.node)) {
        TypeKind elementType = TypeKind::Unknown;
        for (const auto& element : array->elements) {
            const TypeKind current = inferExpression(*element, result);
            if (elementType == TypeKind::Unknown) {
                elementType = current;
            } else if (current != elementType) {
                result.diagnostics.push_back(
                    TypeDiagnostic{"Array elements should have a consistent type.", expr.range});
                break;
            }
        }
        return TypeKind::Array;
    }
    if (const auto* record = std::get_if<RecordLiteral>(&expr.node)) {
        for (const auto& field : record->fields) {
            (void)inferExpression(*field.value, result);
        }
        return TypeKind::Record;
    }
    if (const auto* unary = std::get_if<UnaryExpr>(&expr.node)) {
        const TypeKind operand = inferExpression(*unary->operand, result);
        if (unary->op == "!") {
            if (operand != TypeKind::Bool && operand != TypeKind::Dynamic) {
                result.diagnostics.push_back(
                    TypeDiagnostic{"Logical negation expects a bool operand.", expr.range});
            }
            return TypeKind::Bool;
        }
        if (operand != TypeKind::Int && operand != TypeKind::Float && operand != TypeKind::Dynamic) {
            result.diagnostics.push_back(
                TypeDiagnostic{"Unary '-' expects a numeric operand.", expr.range});
            return TypeKind::Dynamic;
        }
        return operand;
    }
    if (const auto* binary = std::get_if<BinaryExpr>(&expr.node)) {
        const TypeKind left = inferExpression(*binary->left, result);
        if (binary->op == "|>") {
            if (const auto* call = std::get_if<CallExpr>(&binary->right->node)) {
                for (const auto& arg : call->args) {
                    (void)inferExpression(*arg, result);
                }
                if (const auto* identifier = std::get_if<Identifier>(&call->callee->node)) {
                    if (identifier->name == "len") {
                        return TypeKind::Int;
                    }
                    if (identifier->name == "print") {
                        return left;
                    }
                }
                return TypeKind::Dynamic;
            }
            const TypeKind target = inferExpression(*binary->right, result);
            return target == TypeKind::Function ? TypeKind::Dynamic : target;
        }
        const TypeKind right = inferExpression(*binary->right, result);
        if (binary->op == "==" || binary->op == "!=" || binary->op == "<" || binary->op == "<=" ||
            binary->op == ">" || binary->op == ">=") {
            return TypeKind::Bool;
        }
        if (binary->op == "&&" || binary->op == "||") {
            if ((left != TypeKind::Bool && left != TypeKind::Dynamic) ||
                (right != TypeKind::Bool && right != TypeKind::Dynamic)) {
                result.diagnostics.push_back(
                    TypeDiagnostic{"Logical operators expect bool operands.", expr.range});
            }
            return TypeKind::Bool;
        }
        if (binary->op == "+" && left == TypeKind::String && right == TypeKind::String) {
            return TypeKind::String;
        }
        const bool leftNumeric =
            left == TypeKind::Int || left == TypeKind::Float || left == TypeKind::Dynamic;
        const bool rightNumeric =
            right == TypeKind::Int || right == TypeKind::Float || right == TypeKind::Dynamic;
        if (!leftNumeric || !rightNumeric) {
            result.diagnostics.push_back(
                TypeDiagnostic{"Arithmetic operators expect numeric operands.", expr.range});
            return TypeKind::Dynamic;
        }
        return left == TypeKind::Float || right == TypeKind::Float ? TypeKind::Float : TypeKind::Int;
    }
    if (const auto* call = std::get_if<CallExpr>(&expr.node)) {
        const TypeKind callee = inferExpression(*call->callee, result);
        for (const auto& arg : call->args) {
            (void)inferExpression(*arg, result);
        }
        if (const auto* identifier = std::get_if<Identifier>(&call->callee->node)) {
            if (identifier->name == "len") {
                if (call->args.size() != 1) {
                    result.diagnostics.push_back(
                        TypeDiagnostic{"len expects exactly one argument.", expr.range});
                }
                return TypeKind::Int;
            }
            if (identifier->name == "print") {
                return TypeKind::Dynamic;
            }
        }
        if (callee != TypeKind::Function && callee != TypeKind::Dynamic) {
            result.diagnostics.push_back(
                TypeDiagnostic{"Attempted to call a non-function value.", expr.range});
        }
        return TypeKind::Dynamic;
    }
    if (const auto* index = std::get_if<IndexExpr>(&expr.node)) {
        const TypeKind target = inferExpression(*index->target, result);
        const TypeKind indexType = inferExpression(*index->index, result);
        if (indexType != TypeKind::Int && indexType != TypeKind::Dynamic) {
            result.diagnostics.push_back(
                TypeDiagnostic{"Index expressions expect an integer index.", expr.range});
        }
        if (target != TypeKind::Array && target != TypeKind::String && target != TypeKind::Dynamic) {
            result.diagnostics.push_back(
                TypeDiagnostic{"Only arrays and strings can be indexed.", expr.range});
        }
        return TypeKind::Dynamic;
    }
    if (const auto* member = std::get_if<MemberExpr>(&expr.node)) {
        const TypeKind target = inferExpression(*member->target, result);
        if (target != TypeKind::Record && target != TypeKind::Dynamic) {
            result.diagnostics.push_back(
                TypeDiagnostic{"Member access expects a record value.", expr.range});
        }
        return TypeKind::Dynamic;
    }
    return TypeKind::Unknown;
}

std::string_view typeName(TypeKind kind) {
    switch (kind) {
    case TypeKind::Unknown:
        return "unknown";
    case TypeKind::Dynamic:
        return "dynamic";
    case TypeKind::Bool:
        return "bool";
    case TypeKind::Int:
        return "int";
    case TypeKind::Float:
        return "float";
    case TypeKind::String:
        return "string";
    case TypeKind::Array:
        return "array";
    case TypeKind::Record:
        return "record";
    case TypeKind::Function:
        return "function";
    case TypeKind::Optional:
        return "optional";
    case TypeKind::Null:
        return "null";
    }
    return "unknown";
}

} // namespace plang::frontend
