#include "plang/frontend/Lexer.h"

#include <cctype>

namespace plang::frontend {

Lexer::Lexer(std::string_view source) : source_(source) {}

std::vector<Token> Lexer::tokenize() {
    std::vector<Token> tokens;

    while (!isAtEnd()) {
        skipWhitespace();
        if (isAtEnd()) {
            break;
        }

        const std::size_t start = current_;
        const char c = advance();
        switch (c) {
        case '=':
            tokens.push_back(make(match('=') ? TokenKind::EqualEqual : TokenKind::Equal, start));
            break;
        case '!':
            tokens.push_back(make(match('=') ? TokenKind::BangEqual : TokenKind::Bang, start));
            break;
        case '<':
            tokens.push_back(make(match('=') ? TokenKind::LessEqual : TokenKind::Less, start));
            break;
        case '>':
            tokens.push_back(make(match('=') ? TokenKind::GreaterEqual : TokenKind::Greater, start));
            break;
        case '&':
            tokens.push_back(make(match('&') ? TokenKind::AmpAmp : TokenKind::Unknown, start));
            break;
        case '|':
            if (match('|')) {
                tokens.push_back(make(TokenKind::PipePipe, start));
            } else if (match('>')) {
                tokens.push_back(make(TokenKind::PipeGreater, start));
            } else {
                tokens.push_back(make(TokenKind::Unknown, start));
            }
            break;
        case '+':
            tokens.push_back(make(TokenKind::Plus, start));
            break;
        case '-':
            tokens.push_back(make(TokenKind::Minus, start));
            break;
        case '*':
            tokens.push_back(make(TokenKind::Star, start));
            break;
        case '/':
            if (match('/')) {
                while (!isAtEnd() && peek() != '\n') {
                    (void)advance();
                }
            } else {
                tokens.push_back(make(TokenKind::Slash, start));
            }
            break;
        case '%':
            tokens.push_back(make(TokenKind::Percent, start));
            break;
        case '(':
            tokens.push_back(make(TokenKind::LeftParen, start));
            break;
        case ')':
            tokens.push_back(make(TokenKind::RightParen, start));
            break;
        case '{':
            tokens.push_back(make(TokenKind::LeftBrace, start));
            break;
        case '}':
            tokens.push_back(make(TokenKind::RightBrace, start));
            break;
        case '[':
            tokens.push_back(make(TokenKind::LeftBracket, start));
            break;
        case ']':
            tokens.push_back(make(TokenKind::RightBracket, start));
            break;
        case ',':
            tokens.push_back(make(TokenKind::Comma, start));
            break;
        case '.':
            tokens.push_back(make(TokenKind::Dot, start));
            break;
        case ':':
            tokens.push_back(make(TokenKind::Colon, start));
            break;
        case ';':
            tokens.push_back(make(TokenKind::Semicolon, start));
            break;
        case '"':
            tokens.push_back(string(start));
            break;
        default:
            if (std::isalpha(static_cast<unsigned char>(c)) || c == '_') {
                tokens.push_back(identifier(start));
            } else if (std::isdigit(static_cast<unsigned char>(c))) {
                tokens.push_back(number(start));
            } else {
                tokens.push_back(make(TokenKind::Unknown, start));
            }
            break;
        }
    }

    tokens.push_back(Token{TokenKind::End, "", current_});
    return tokens;
}

bool Lexer::isAtEnd() const {
    return current_ >= source_.size();
}

char Lexer::peek() const {
    return isAtEnd() ? '\0' : source_[current_];
}

char Lexer::peekNext() const {
    return current_ + 1 >= source_.size() ? '\0' : source_[current_ + 1];
}

char Lexer::advance() {
    return source_[current_++];
}

bool Lexer::match(char expected) {
    if (isAtEnd() || source_[current_] != expected) {
        return false;
    }
    ++current_;
    return true;
}

void Lexer::skipWhitespace() {
    while (!isAtEnd() && std::isspace(static_cast<unsigned char>(peek()))) {
        (void)advance();
    }
}

Token Lexer::identifier(std::size_t start) {
    while (!isAtEnd() &&
           (std::isalnum(static_cast<unsigned char>(peek())) || peek() == '_')) {
        (void)advance();
    }

    auto text = std::string(source_.substr(start, current_ - start));
    TokenKind kind = TokenKind::Identifier;
    if (text == "let") {
        kind = TokenKind::Let;
    } else if (text == "true") {
        kind = TokenKind::True;
    } else if (text == "false") {
        kind = TokenKind::False;
    } else if (text == "null") {
        kind = TokenKind::Null;
    }
    return Token{kind, std::move(text), start};
}

Token Lexer::number(std::size_t start) {
    while (!isAtEnd() && std::isdigit(static_cast<unsigned char>(peek()))) {
        (void)advance();
    }
    if (!isAtEnd() && peek() == '.' && std::isdigit(static_cast<unsigned char>(peekNext()))) {
        (void)advance();
        while (!isAtEnd() && std::isdigit(static_cast<unsigned char>(peek()))) {
            (void)advance();
        }
    }
    return make(TokenKind::Number, start);
}

Token Lexer::string(std::size_t start) {
    while (!isAtEnd() && peek() != '"') {
        (void)advance();
    }
    if (!isAtEnd()) {
        (void)advance();
    }
    return make(TokenKind::String, start);
}

Token Lexer::make(TokenKind kind, std::size_t start) const {
    return Token{kind, std::string(source_.substr(start, current_ - start)), start};
}

std::string_view tokenKindName(TokenKind kind) {
    switch (kind) {
    case TokenKind::End:
        return "end";
    case TokenKind::Identifier:
        return "identifier";
    case TokenKind::Number:
        return "number";
    case TokenKind::String:
        return "string";
    case TokenKind::Let:
        return "let";
    case TokenKind::True:
        return "true";
    case TokenKind::False:
        return "false";
    case TokenKind::Null:
        return "null";
    case TokenKind::Equal:
        return "=";
    case TokenKind::EqualEqual:
        return "==";
    case TokenKind::Bang:
        return "!";
    case TokenKind::BangEqual:
        return "!=";
    case TokenKind::Less:
        return "<";
    case TokenKind::LessEqual:
        return "<=";
    case TokenKind::Greater:
        return ">";
    case TokenKind::GreaterEqual:
        return ">=";
    case TokenKind::AmpAmp:
        return "&&";
    case TokenKind::PipePipe:
        return "||";
    case TokenKind::PipeGreater:
        return "|>";
    case TokenKind::Plus:
        return "+";
    case TokenKind::Minus:
        return "-";
    case TokenKind::Star:
        return "*";
    case TokenKind::Slash:
        return "/";
    case TokenKind::Percent:
        return "%";
    case TokenKind::LeftParen:
        return "(";
    case TokenKind::RightParen:
        return ")";
    case TokenKind::LeftBrace:
        return "{";
    case TokenKind::RightBrace:
        return "}";
    case TokenKind::LeftBracket:
        return "[";
    case TokenKind::RightBracket:
        return "]";
    case TokenKind::Comma:
        return ",";
    case TokenKind::Dot:
        return ".";
    case TokenKind::Colon:
        return ":";
    case TokenKind::Semicolon:
        return ";";
    case TokenKind::Unknown:
        return "unknown";
    }
    return "unknown";
}

} // namespace plang::frontend
