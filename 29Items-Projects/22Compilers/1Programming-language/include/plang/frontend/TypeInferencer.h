#pragma once

#include "plang/frontend/Ast.h"

#include <string>
#include <unordered_map>
#include <vector>

namespace plang::frontend {

enum class TypeKind {
    Unknown,
    Dynamic,
    Bool,
    Int,
    Float,
    String,
    Array,
    Record,
    Function,
    Optional,
    Null
};

struct TypeDiagnostic {
    std::string message;
    SourceRange range;
};

struct TypeCheckResult {
    std::unordered_map<std::string, TypeKind> symbols;
    std::vector<TypeDiagnostic> diagnostics;
};

class TypeInferencer {
public:
    [[nodiscard]] TypeCheckResult infer(const Module& module) const;

private:
    [[nodiscard]] TypeKind inferExpression(const Expr& expr, TypeCheckResult& result) const;
};

std::string_view typeName(TypeKind kind);

} // namespace plang::frontend
