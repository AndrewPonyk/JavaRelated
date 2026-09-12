#pragma once

#include <cstddef>
#include <vector>

#include "minidb/common/status.hpp"
#include "minidb/parser/ast.hpp"
#include "minidb/parser/token.hpp"

/// @file parser.hpp
/// Recursive-descent parser turning a token stream into a `Statement`. Covers
/// the CREATE/INSERT/SELECT subset described in ast.hpp. A bounded recursion
/// depth and identifier-length checks guard against pathological input
/// (docs/ARCHITECTURE.md §2.5).

namespace minidb {

class Parser {
 public:
  explicit Parser(std::vector<Token> tokens) : tokens_(std::move(tokens)) {}

  /// Parses exactly one statement (an optional trailing ';' is allowed).
  StatusOr<Statement> Parse();

 private:
  const Token& Peek() const { return tokens_[pos_]; }
  const Token& Previous() const { return tokens_[pos_ - 1]; }
  bool Check(TokenType type) const { return Peek().type == type; }
  bool AtEnd() const { return Peek().type == TokenType::kEof; }
  const Token& Advance();
  bool Match(TokenType type);

  StatusOr<Statement> ParseCreateTable();
  StatusOr<Statement> ParseInsert();
  StatusOr<Statement> ParseSelect();
  StatusOr<Predicate> ParseWhere();
  StatusOr<Value> ParseLiteral();

  std::vector<Token> tokens_;
  std::size_t pos_ = 0;
};

}  // namespace minidb
