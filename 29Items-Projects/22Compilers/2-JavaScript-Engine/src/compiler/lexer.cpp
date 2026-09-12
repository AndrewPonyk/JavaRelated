#include "jsengine/compiler/lexer.h"

#include <cctype>

namespace jsengine::compiler {

namespace {

bool isIdentifierStart(char value) {
    return std::isalpha(static_cast<unsigned char>(value)) != 0 || value == '_' || value == '$';
}

bool isIdentifierPart(char value) {
    return isIdentifierStart(value) || std::isdigit(static_cast<unsigned char>(value)) != 0;
}

TokenType keywordOrIdentifier(const std::string& text) {
    if (text == "let") {
        return TokenType::KeywordLet;
    }
    if (text == "var") {
        return TokenType::KeywordVar;
    }
    if (text == "const") {
        return TokenType::KeywordConst;
    }
    if (text == "function") {
        return TokenType::KeywordFunction;
    }
    if (text == "return") {
        return TokenType::KeywordReturn;
    }
    if (text == "true") {
        return TokenType::KeywordTrue;
    }
    if (text == "false") {
        return TokenType::KeywordFalse;
    }
    if (text == "null") {
        return TokenType::KeywordNull;
    }
    if (text == "undefined") {
        return TokenType::KeywordUndefined;
    }
    return TokenType::Identifier;
}

} // namespace

std::vector<Token> Lexer::tokenize(const std::string& source) const {
    std::vector<Token> tokens;
    std::size_t index = 0;
    std::size_t line = 1;
    std::size_t column = 1;

    while (index < source.size()) {
        const char current = source[index];
        if (current == '\n') {
            ++line;
            column = 1;
            ++index;
            continue;
        }
        if (std::isspace(static_cast<unsigned char>(current)) != 0) {
            ++column;
            ++index;
            continue;
        }

        const auto tokenLine = line;
        const auto tokenColumn = column;

        if (isIdentifierStart(current)) {
            std::string text;
            while (index < source.size() && isIdentifierPart(source[index])) {
                text.push_back(source[index]);
                ++index;
                ++column;
            }
            tokens.push_back({keywordOrIdentifier(text), text, tokenLine, tokenColumn});
            continue;
        }

        if (std::isdigit(static_cast<unsigned char>(current)) != 0) {
            std::string text;
            while (index < source.size() && std::isdigit(static_cast<unsigned char>(source[index])) != 0) {
                text.push_back(source[index]);
                ++index;
                ++column;
            }
            if (index < source.size() && source[index] == '.') {
                text.push_back(source[index]);
                ++index;
                ++column;
                if (index >= source.size() || std::isdigit(static_cast<unsigned char>(source[index])) == 0) {
                    tokens.push_back({TokenType::Invalid, text, tokenLine, tokenColumn});
                    continue;
                }
                while (index < source.size() && std::isdigit(static_cast<unsigned char>(source[index])) != 0) {
                    text.push_back(source[index]);
                    ++index;
                    ++column;
                }
            }
            tokens.push_back({TokenType::Number, text, tokenLine, tokenColumn});
            continue;
        }

        if (current == '/' && index + 1 < source.size() && source[index + 1] == '/') {
            while (index < source.size() && source[index] != '\n') {
                ++index;
                ++column;
            }
            continue;
        }

        if (current == '/' && index + 1 < source.size() && source[index + 1] == '*') {
            index += 2;
            column += 2;
            bool closed = false;
            while (index + 1 < source.size()) {
                if (source[index] == '*' && source[index + 1] == '/') {
                    index += 2;
                    column += 2;
                    closed = true;
                    break;
                }
                if (source[index] == '\n') {
                    ++line;
                    column = 1;
                } else {
                    ++column;
                }
                ++index;
            }
            if (!closed) {
                tokens.push_back({TokenType::Invalid, "unterminated comment", tokenLine, tokenColumn});
            }
            continue;
        }

        if (current == '"' || current == '\'') {
            const char quote = current;
            std::string text;
            bool invalidString = false;
            ++index;
            ++column;
            while (index < source.size() && source[index] != quote) {
                if (source[index] == '\n') {
                    tokens.push_back({TokenType::Invalid, "unterminated string", tokenLine, tokenColumn});
                    invalidString = true;
                    ++line;
                    column = 1;
                    ++index;
                    break;
                }
                if (source[index] == '\\' && index + 1 < source.size()) {
                    const char escaped = source[index + 1];
                    switch (escaped) {
                    case 'n':
                        text.push_back('\n');
                        break;
                    case 't':
                        text.push_back('\t');
                        break;
                    default:
                        text.push_back(escaped);
                        break;
                    }
                    index += 2;
                    column += 2;
                    continue;
                }
                text.push_back(source[index]);
                ++index;
                ++column;
            }
            if (invalidString) {
                continue;
            }
            if (index < source.size()) {
                ++index;
                ++column;
                tokens.push_back({TokenType::String, text, tokenLine, tokenColumn});
            } else {
                tokens.push_back({TokenType::Invalid, text, tokenLine, tokenColumn});
            }
            continue;
        }

        tokens.push_back({TokenType::Symbol, std::string(1, current), tokenLine, tokenColumn});
        ++index;
        ++column;
    }

    tokens.push_back({TokenType::EndOfFile, "", line, column});
    return tokens;
}

} // namespace jsengine::compiler
