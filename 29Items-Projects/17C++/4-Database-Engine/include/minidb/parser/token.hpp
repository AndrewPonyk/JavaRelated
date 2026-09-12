#pragma once

#include <cstddef>
#include <string>

/// @file token.hpp
/// Lexical tokens for the SQL subset. The lexer (parser/lexer.hpp) lowercases
/// identifiers and matches keywords case-insensitively.

namespace minidb {

enum class TokenType {
  // Literals & identifiers
  kIdentifier,
  kNumber,  ///< integer literal
  kString,  ///< single-quoted string literal

  // Keywords
  kSelect,
  kFrom,
  kWhere,
  kInsert,
  kInto,
  kValues,
  kCreate,
  kTable,
  kExplain,
  kAnd,
  kOr,
  kTrue,
  kFalse,
  kIntType,
  kBigIntType,
  kVarcharType,
  kBoolType,

  // Punctuation & operators
  kComma,
  kLParen,
  kRParen,
  kStar,
  kSemicolon,
  kEq,
  kNe,
  kLt,
  kLe,
  kGt,
  kGe,

  kEof,
  kInvalid,
};

const char* TokenTypeToString(TokenType type);

struct Token {
  TokenType type = TokenType::kInvalid;
  std::string lexeme;  ///< Source text (identifiers already lowercased).
  std::size_t
      position;  ///< Byte offset in the original query, for diagnostics.
};

}  // namespace minidb
