#pragma once

#include <string>
#include <vector>

#include "minidb/common/status.hpp"
#include "minidb/parser/token.hpp"

/// @file lexer.hpp
/// Turns SQL text into a token stream. Fully implemented; exercised by
/// tests/test_lexer.cpp.

namespace minidb {

class Lexer {
 public:
  explicit Lexer(std::string sql) : sql_(std::move(sql)) {}

  /// Tokenizes the whole input, ending with a kEof token. Returns a
  /// kSyntaxError status on an unterminated string or stray character.
  StatusOr<std::vector<Token>> Tokenize();

 private:
  bool AtEnd() const { return pos_ >= sql_.size(); }
  char Peek() const { return AtEnd() ? '\0' : sql_[pos_]; }
  char PeekNext() const {
    return pos_ + 1 < sql_.size() ? sql_[pos_ + 1] : '\0';
  }

  std::string sql_;
  std::size_t pos_ = 0;
};

}  // namespace minidb
