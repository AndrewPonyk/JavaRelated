// Unit tests for the SQL lexer. Fully implemented module.

#include <gtest/gtest.h>

#include <string>
#include <vector>

#include "minidb/parser/lexer.hpp"

using minidb::Lexer;
using minidb::StatusCode;
using minidb::Token;
using minidb::TokenType;

namespace {
std::vector<Token> Lex(const std::string& sql) {
  auto result = Lexer(sql).Tokenize();
  EXPECT_TRUE(result.ok()) << result.status().ToString();
  return result.ok() ? std::move(result).value() : std::vector<Token>{};
}
}  // namespace

TEST(Lexer, KeywordsAreCaseInsensitiveAndIdentifiersLowercased) {
  auto t = Lex("Select * FROM Users");
  ASSERT_GE(t.size(), 5u);
  EXPECT_EQ(t[0].type, TokenType::kSelect);
  EXPECT_EQ(t[1].type, TokenType::kStar);
  EXPECT_EQ(t[2].type, TokenType::kFrom);
  EXPECT_EQ(t[3].type, TokenType::kIdentifier);
  EXPECT_EQ(t[3].lexeme, "users");  // identifiers are lowercased
  EXPECT_EQ(t.back().type, TokenType::kEof);
}

TEST(Lexer, NumbersAndEscapedStrings) {
  auto t = Lex("INSERT INTO t VALUES (42, 'Ada''s')");
  bool saw_number = false;
  bool saw_string = false;
  for (const auto& tok : t) {
    if (tok.type == TokenType::kNumber && tok.lexeme == "42") saw_number = true;
    if (tok.type == TokenType::kString && tok.lexeme == "Ada's")
      saw_string = true;
  }
  EXPECT_TRUE(saw_number);
  EXPECT_TRUE(saw_string);  // '' collapses to a single quote
}

TEST(Lexer, ComparisonOperators) {
  auto t = Lex("a <= b >= c <> d != e < f > g = h");
  std::vector<TokenType> ops;
  for (const auto& tok : t) {
    if (tok.type != TokenType::kIdentifier && tok.type != TokenType::kEof) {
      ops.push_back(tok.type);
    }
  }
  const std::vector<TokenType> want = {
      TokenType::kLe, TokenType::kGe, TokenType::kNe, TokenType::kNe,
      TokenType::kLt, TokenType::kGt, TokenType::kEq};
  ASSERT_EQ(ops.size(), want.size());
  for (std::size_t i = 0; i < want.size(); ++i) EXPECT_EQ(ops[i], want[i]);
}

TEST(Lexer, LineCommentsAreSkipped) {
  auto t = Lex("SELECT a -- this is a comment\n FROM t");
  ASSERT_EQ(t.size(), 5u);  // SELECT a FROM t EOF
  EXPECT_EQ(t[0].type, TokenType::kSelect);
  EXPECT_EQ(t[1].lexeme, "a");
  EXPECT_EQ(t[2].type, TokenType::kFrom);
  EXPECT_EQ(t[3].lexeme, "t");
}

TEST(Lexer, UnterminatedStringIsASyntaxError) {
  auto result = Lexer("'oops").Tokenize();
  ASSERT_FALSE(result.ok());
  EXPECT_EQ(result.status().code(), StatusCode::kSyntaxError);
}
