#pragma once

#include <string>
#include <string_view>
#include <vector>

namespace plang::frontend {

enum class TokenKind {
    End,
    Identifier,
    Number,
    String,
    Let,
    True,
    False,
    Null,
    Equal,
    EqualEqual,
    Bang,
    BangEqual,
    Less,
    LessEqual,
    Greater,
    GreaterEqual,
    AmpAmp,
    PipePipe,
    PipeGreater,
    Plus,
    Minus,
    Star,
    Slash,
    Percent,
    LeftParen,
    RightParen,
    LeftBrace,
    RightBrace,
    LeftBracket,
    RightBracket,
    Comma,
    Dot,
    Colon,
    Semicolon,
    Unknown
};

struct Token {
    TokenKind kind{TokenKind::Unknown};
    std::string lexeme;
    std::size_t offset{0};
};

class Lexer {
public:
    explicit Lexer(std::string_view source);

    [[nodiscard]] std::vector<Token> tokenize();

private:
    [[nodiscard]] bool isAtEnd() const;
    [[nodiscard]] char peek() const;
    [[nodiscard]] char peekNext() const;
    [[nodiscard]] char advance();
    [[nodiscard]] bool match(char expected);
    void skipWhitespace();
    Token identifier(std::size_t start);
    Token number(std::size_t start);
    Token string(std::size_t start);
    Token make(TokenKind kind, std::size_t start) const;

    std::string_view source_;
    std::size_t current_{0};
};

std::string_view tokenKindName(TokenKind kind);

} // namespace plang::frontend
