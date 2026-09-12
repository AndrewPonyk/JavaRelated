#include "minidb/parser/lexer.hpp"

#include <cctype>
#include <string>
#include <unordered_map>

namespace minidb {
namespace {

TokenType KeywordOrIdentifier(const std::string& lowered) {
  static const std::unordered_map<std::string, TokenType> kKeywords = {
      {"select", TokenType::kSelect},
      {"from", TokenType::kFrom},
      {"where", TokenType::kWhere},
      {"insert", TokenType::kInsert},
      {"into", TokenType::kInto},
      {"values", TokenType::kValues},
      {"create", TokenType::kCreate},
      {"table", TokenType::kTable},
      {"explain", TokenType::kExplain},
      {"and", TokenType::kAnd},
      {"or", TokenType::kOr},
      {"true", TokenType::kTrue},
      {"false", TokenType::kFalse},
      {"int", TokenType::kIntType},
      {"integer", TokenType::kIntType},
      {"bigint", TokenType::kBigIntType},
      {"varchar", TokenType::kVarcharType},
      {"text", TokenType::kVarcharType},
      {"bool", TokenType::kBoolType},
      {"boolean", TokenType::kBoolType},
  };
  auto it = kKeywords.find(lowered);
  return it == kKeywords.end() ? TokenType::kIdentifier : it->second;
}

}  // namespace

const char* TokenTypeToString(TokenType type) {
  switch (type) {
    case TokenType::kIdentifier:
      return "IDENTIFIER";
    case TokenType::kNumber:
      return "NUMBER";
    case TokenType::kString:
      return "STRING";
    case TokenType::kSelect:
      return "SELECT";
    case TokenType::kFrom:
      return "FROM";
    case TokenType::kWhere:
      return "WHERE";
    case TokenType::kInsert:
      return "INSERT";
    case TokenType::kInto:
      return "INTO";
    case TokenType::kValues:
      return "VALUES";
    case TokenType::kCreate:
      return "CREATE";
    case TokenType::kTable:
      return "TABLE";
    case TokenType::kExplain:
      return "EXPLAIN";
    case TokenType::kAnd:
      return "AND";
    case TokenType::kOr:
      return "OR";
    case TokenType::kTrue:
      return "TRUE";
    case TokenType::kFalse:
      return "FALSE";
    case TokenType::kIntType:
      return "INT";
    case TokenType::kBigIntType:
      return "BIGINT";
    case TokenType::kVarcharType:
      return "VARCHAR";
    case TokenType::kBoolType:
      return "BOOL";
    case TokenType::kComma:
      return "COMMA";
    case TokenType::kLParen:
      return "LPAREN";
    case TokenType::kRParen:
      return "RPAREN";
    case TokenType::kStar:
      return "STAR";
    case TokenType::kSemicolon:
      return "SEMICOLON";
    case TokenType::kEq:
      return "EQ";
    case TokenType::kNe:
      return "NE";
    case TokenType::kLt:
      return "LT";
    case TokenType::kLe:
      return "LE";
    case TokenType::kGt:
      return "GT";
    case TokenType::kGe:
      return "GE";
    case TokenType::kEof:
      return "EOF";
    case TokenType::kInvalid:
      return "INVALID";
  }
  return "INVALID";
}

StatusOr<std::vector<Token>> Lexer::Tokenize() {
  std::vector<Token> tokens;

  while (!AtEnd()) {
    const char c = Peek();

    // Whitespace.
    if (std::isspace(static_cast<unsigned char>(c)) != 0) {
      ++pos_;
      continue;
    }
    // SQL line comment: -- to end of line.
    if (c == '-' && PeekNext() == '-') {
      while (!AtEnd() && Peek() != '\n') ++pos_;
      continue;
    }

    const std::size_t start = pos_;

    // Identifier or keyword.
    if (std::isalpha(static_cast<unsigned char>(c)) != 0 || c == '_') {
      std::string id;
      while (!AtEnd() &&
             (std::isalnum(static_cast<unsigned char>(Peek())) != 0 ||
              Peek() == '_')) {
        id +=
            static_cast<char>(std::tolower(static_cast<unsigned char>(Peek())));
        ++pos_;
      }
      tokens.push_back({KeywordOrIdentifier(id), id, start});
      continue;
    }

    // Integer literal (optionally signed).
    if (std::isdigit(static_cast<unsigned char>(c)) != 0 ||
        (c == '-' &&
         std::isdigit(static_cast<unsigned char>(PeekNext())) != 0)) {
      std::string num;
      if (c == '-') {
        num += '-';
        ++pos_;
      }
      while (!AtEnd() &&
             std::isdigit(static_cast<unsigned char>(Peek())) != 0) {
        num += Peek();
        ++pos_;
      }
      tokens.push_back({TokenType::kNumber, num, start});
      continue;
    }

    // String literal: '...'. A doubled '' is an escaped single quote.
    if (c == '\'') {
      ++pos_;  // consume opening quote
      std::string s;
      bool closed = false;
      while (!AtEnd()) {
        const char d = Peek();
        if (d == '\'') {
          if (PeekNext() == '\'') {  // escaped quote
            s += '\'';
            pos_ += 2;
            continue;
          }
          ++pos_;  // consume closing quote
          closed = true;
          break;
        }
        s += d;
        ++pos_;
      }
      if (!closed) {
        return Status::SyntaxError("unterminated string literal at offset " +
                                   std::to_string(start));
      }
      tokens.push_back({TokenType::kString, s, start});
      continue;
    }

    // Operators and punctuation.
    switch (c) {
      case ',':
        tokens.push_back({TokenType::kComma, ",", start});
        ++pos_;
        break;
      case '(':
        tokens.push_back({TokenType::kLParen, "(", start});
        ++pos_;
        break;
      case ')':
        tokens.push_back({TokenType::kRParen, ")", start});
        ++pos_;
        break;
      case '*':
        tokens.push_back({TokenType::kStar, "*", start});
        ++pos_;
        break;
      case ';':
        tokens.push_back({TokenType::kSemicolon, ";", start});
        ++pos_;
        break;
      case '=':
        tokens.push_back({TokenType::kEq, "=", start});
        ++pos_;
        break;
      case '!':
        if (PeekNext() == '=') {
          tokens.push_back({TokenType::kNe, "!=", start});
          pos_ += 2;
        } else {
          return Status::SyntaxError("unexpected '!' at offset " +
                                     std::to_string(start));
        }
        break;
      case '<':
        if (PeekNext() == '=') {
          tokens.push_back({TokenType::kLe, "<=", start});
          pos_ += 2;
        } else if (PeekNext() == '>') {
          tokens.push_back({TokenType::kNe, "<>", start});
          pos_ += 2;
        } else {
          tokens.push_back({TokenType::kLt, "<", start});
          ++pos_;
        }
        break;
      case '>':
        if (PeekNext() == '=') {
          tokens.push_back({TokenType::kGe, ">=", start});
          pos_ += 2;
        } else {
          tokens.push_back({TokenType::kGt, ">", start});
          ++pos_;
        }
        break;
      default:
        return Status::SyntaxError(std::string("unexpected character '") + c +
                                   "' at offset " + std::to_string(start));
    }
  }

  tokens.push_back({TokenType::kEof, "", pos_});
  return tokens;
}

}  // namespace minidb
