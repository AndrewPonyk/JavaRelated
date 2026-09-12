#pragma once

#include <cstddef>
#include <string>
#include <vector>

namespace jsengine::compiler {

enum class TokenType {
    Identifier,
    Number,
    String,
    KeywordVar,
    KeywordLet,
    KeywordConst,
    KeywordFunction,
    KeywordReturn,
    KeywordTrue,
    KeywordFalse,
    KeywordNull,
    KeywordUndefined,
    Symbol,
    EndOfFile,
    Invalid
};

struct Token {
    TokenType type{TokenType::Invalid};
    std::string lexeme;
    std::size_t line{1};
    std::size_t column{1};
};

class Lexer {
  public:
    std::vector<Token> tokenize(const std::string& source) const;
};

} // namespace jsengine::compiler
