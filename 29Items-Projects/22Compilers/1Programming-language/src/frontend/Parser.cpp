#include "plang/frontend/Parser.h"

#include <cstdlib>
#include <string>
#include <utility>

namespace plang::frontend {

namespace {

std::string unescapeString(const std::string& lexeme) {
    std::string out;
    const std::size_t begin = lexeme.size() >= 2 && lexeme.front() == '"' ? 1 : 0;
    const std::size_t end = lexeme.size() >= 2 && lexeme.back() == '"' ? lexeme.size() - 1
                                                                       : lexeme.size();
    for (std::size_t index = begin; index < end; ++index) {
        if (lexeme[index] != '\\' || index + 1 >= end) {
            out.push_back(lexeme[index]);
            continue;
        }
        const char escaped = lexeme[++index];
        switch (escaped) {
        case 'n':
            out.push_back('\n');
            break;
        case 'r':
            out.push_back('\r');
            break;
        case 't':
            out.push_back('\t');
            break;
        case '"':
        case '\\':
            out.push_back(escaped);
            break;
        default:
            out.push_back(escaped);
            break;
        }
    }
    return out;
}

} // namespace

Parser::Parser(std::vector<Token> tokens) : tokens_(std::move(tokens)) {}

ParseResult Parser::parseModule() {
    Module module;
    while (!isAtEnd()) {
        module.statements.push_back(parseStatement());
    }
    return ParseResult{std::move(module), std::move(diagnostics_)};
}

bool Parser::isAtEnd() const {
    return peek().kind == TokenKind::End;
}

const Token& Parser::peek() const {
    return tokens_[current_];
}

const Token& Parser::previous() const {
    return tokens_[current_ - 1];
}

bool Parser::check(TokenKind kind) const {
    return !isAtEnd() && peek().kind == kind;
}

bool Parser::match(TokenKind kind) {
    if (!check(kind)) {
        return false;
    }
    ++current_;
    return true;
}

Token Parser::consume(TokenKind kind, std::string message) {
    if (check(kind)) {
        ++current_;
        return previous();
    }
    diagnostic(std::move(message), peek().offset);
    return Token{kind, "", peek().offset};
}

Statement Parser::parseStatement() {
    if (match(TokenKind::Let)) {
        return parseLetStatement();
    }

    auto expression = parseExpression();
    consume(TokenKind::Semicolon, "Expected ';' after expression.");
    return Statement{ExpressionStatement{std::move(expression)}};
}

Statement Parser::parseLetStatement() {
    const Token name = consume(TokenKind::Identifier, "Expected binding name after 'let'.");
    consume(TokenKind::Equal, "Expected '=' after binding name.");
    auto initializer = parseExpression();
    consume(TokenKind::Semicolon, "Expected ';' after let binding.");
    return Statement{LetStatement{name.lexeme, std::move(initializer)}};
}

ExprPtr Parser::parseExpression(int minBindingPower) {
    auto left = parsePrefix();

    while (!isAtEnd()) {
        const int bindingPower = infixBindingPower(peek().kind);
        if (bindingPower < minBindingPower) {
            break;
        }

        const Token op = peek();
        ++current_;
        auto right = parseExpression(bindingPower + 1);
        auto binary = std::make_unique<Expr>();
        binary->range = SourceRange{left->range.begin, right->range.end};
        binary->node = BinaryExpr{op.lexeme, std::move(left), std::move(right)};
        left = std::move(binary);
    }

    return left;
}

ExprPtr Parser::parsePrefix() {
    if (match(TokenKind::Bang) || match(TokenKind::Minus)) {
        const Token op = previous();
        auto operand = parseExpression(40);
        auto expr = std::make_unique<Expr>();
        expr->range = SourceRange{op.offset, operand->range.end};
        expr->node = UnaryExpr{op.lexeme, std::move(operand)};
        return expr;
    }

    return parsePostfix(parsePrimary());
}

ExprPtr Parser::parsePostfix(ExprPtr expr) {
    while (!isAtEnd()) {
        if (match(TokenKind::LeftParen)) {
            const Token leftParen = previous();
            std::vector<ExprPtr> args;
            if (!check(TokenKind::RightParen)) {
                do {
                    args.push_back(parseExpression());
                } while (match(TokenKind::Comma));
            }
            const Token rightParen =
                consume(TokenKind::RightParen, "Expected ')' after call arguments.");
            auto call = std::make_unique<Expr>();
            call->range = SourceRange{expr->range.begin,
                                      rightParen.lexeme.empty() ? leftParen.offset + 1
                                                                : rightParen.offset + 1};
            call->node = CallExpr{std::move(expr), std::move(args)};
            expr = std::move(call);
            continue;
        }

        if (match(TokenKind::LeftBracket)) {
            auto index = parseExpression();
            const Token rightBracket =
                consume(TokenKind::RightBracket, "Expected ']' after index expression.");
            auto indexed = std::make_unique<Expr>();
            indexed->range = SourceRange{expr->range.begin,
                                         rightBracket.lexeme.empty() ? index->range.end
                                                                     : rightBracket.offset + 1};
            indexed->node = IndexExpr{std::move(expr), std::move(index)};
            expr = std::move(indexed);
            continue;
        }

        if (match(TokenKind::Dot)) {
            const Token member = consume(TokenKind::Identifier, "Expected member name after '.'.");
            auto accessed = std::make_unique<Expr>();
            accessed->range =
                SourceRange{expr->range.begin, member.offset + member.lexeme.size()};
            accessed->node = MemberExpr{std::move(expr), member.lexeme};
            expr = std::move(accessed);
            continue;
        }

        break;
    }

    return expr;
}

ExprPtr Parser::parsePrimary() {
    if (match(TokenKind::Number)) {
        const Token token = previous();
        const bool isInteger = token.lexeme.find('.') == std::string::npos;
        return makeNumber(std::strtod(token.lexeme.c_str(), nullptr),
                          SourceRange{token.offset, token.offset + token.lexeme.size()},
                          isInteger);
    }

    if (match(TokenKind::True) || match(TokenKind::False)) {
        const Token token = previous();
        return makeBool(token.kind == TokenKind::True,
                        SourceRange{token.offset, token.offset + token.lexeme.size()});
    }

    if (match(TokenKind::Null)) {
        const Token token = previous();
        return makeNull(SourceRange{token.offset, token.offset + token.lexeme.size()});
    }

    if (match(TokenKind::Identifier)) {
        const Token token = previous();
        return makeIdentifier(token.lexeme,
                              SourceRange{token.offset, token.offset + token.lexeme.size()});
    }

    if (match(TokenKind::String)) {
        const Token token = previous();
        return makeString(unescapeString(token.lexeme),
                          SourceRange{token.offset, token.offset + token.lexeme.size()});
    }

    if (match(TokenKind::LeftBracket)) {
        return parseArrayLiteral(previous());
    }

    if (match(TokenKind::LeftBrace)) {
        return parseRecordLiteral(previous());
    }

    if (match(TokenKind::LeftParen)) {
        auto expr = parseExpression();
        consume(TokenKind::RightParen, "Expected ')' after grouped expression.");
        return expr;
    }

    diagnostic("Expected expression.", peek().offset);
    const auto offset = peek().offset;
    if (!isAtEnd()) {
        ++current_;
    }
    return makeNumber(0.0, SourceRange{offset, offset}, true);
}

ExprPtr Parser::parseArrayLiteral(const Token& leftBracket) {
    std::vector<ExprPtr> elements;
    if (!check(TokenKind::RightBracket)) {
        do {
            elements.push_back(parseExpression());
        } while (match(TokenKind::Comma));
    }
    const Token rightBracket =
        consume(TokenKind::RightBracket, "Expected ']' after array literal.");
    auto expr = std::make_unique<Expr>();
    expr->range = SourceRange{leftBracket.offset,
                              rightBracket.lexeme.empty() ? leftBracket.offset + 1
                                                          : rightBracket.offset + 1};
    expr->node = ArrayLiteral{std::move(elements)};
    return expr;
}

ExprPtr Parser::parseRecordLiteral(const Token& leftBrace) {
    std::vector<RecordField> fields;
    if (!check(TokenKind::RightBrace)) {
        do {
            const Token name = consume(TokenKind::Identifier, "Expected record field name.");
            consume(TokenKind::Colon, "Expected ':' after record field name.");
            fields.push_back(RecordField{name.lexeme, parseExpression()});
        } while (match(TokenKind::Comma));
    }
    const Token rightBrace =
        consume(TokenKind::RightBrace, "Expected '}' after record literal.");
    auto expr = std::make_unique<Expr>();
    expr->range = SourceRange{leftBrace.offset,
                              rightBrace.lexeme.empty() ? leftBrace.offset + 1
                                                        : rightBrace.offset + 1};
    expr->node = RecordLiteral{std::move(fields)};
    return expr;
}

int Parser::infixBindingPower(TokenKind kind) const {
    switch (kind) {
    case TokenKind::PipeGreater:
        return 4;
    case TokenKind::PipePipe:
        return 5;
    case TokenKind::AmpAmp:
        return 6;
    case TokenKind::EqualEqual:
    case TokenKind::BangEqual:
        return 7;
    case TokenKind::Less:
    case TokenKind::LessEqual:
    case TokenKind::Greater:
    case TokenKind::GreaterEqual:
        return 8;
    case TokenKind::Plus:
    case TokenKind::Minus:
        return 10;
    case TokenKind::Star:
    case TokenKind::Slash:
    case TokenKind::Percent:
        return 20;
    default:
        return -1;
    }
}

void Parser::synchronize() {
    while (!isAtEnd()) {
        if (previous().kind == TokenKind::Semicolon) {
            return;
        }
        if (peek().kind == TokenKind::Let) {
            return;
        }
        ++current_;
    }
}

void Parser::diagnostic(std::string message, std::size_t offset) {
    diagnostics_.push_back(ParseDiagnostic{std::move(message), offset});
}

} // namespace plang::frontend
