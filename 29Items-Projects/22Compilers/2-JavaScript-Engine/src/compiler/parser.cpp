#include "jsengine/compiler/parser.h"

#include <optional>
#include <string>
#include <utility>

namespace jsengine::compiler {

namespace {

class ParserImpl {
  public:
    explicit ParserImpl(const std::vector<Token>& tokens) : tokens_(tokens) {}

    ParseResult parse() {
        AstNode program{.kind = AstKind::Program, .text = "Program", .children = {}};
        while (!isAtEnd()) {
            if (matchSymbol(";")) {
                continue;
            }
            auto statement = parseStatement();
            if (!statement.has_value()) {
                return {.ok = false, .nodes = {}, .error = error_};
            }
            program.children.push_back(std::move(*statement));
        }
        return {.ok = true, .nodes = {std::move(program)}, .error = ""};
    }

  private:
    std::optional<AstNode> parseStatement() {
        if (match(TokenType::KeywordLet) || match(TokenType::KeywordConst) || match(TokenType::KeywordVar)) {
            const auto declarationToken = previous();
            const bool constant = declarationToken.type == TokenType::KeywordConst;
            if (!check(TokenType::Identifier)) {
                fail("Expected identifier after variable declaration keyword");
                return std::nullopt;
            }
            const auto name = advance();
            AstNode declaration{.kind = AstKind::VariableDeclaration,
                                .text = name.lexeme,
                                .line = declarationToken.line,
                                .column = declarationToken.column,
                                .children = {},
                                .constant = constant};
            if (matchSymbol("=")) {
                auto initializer = parseExpression();
                if (!initializer.has_value()) {
                    return std::nullopt;
                }
                declaration.children.push_back(std::move(*initializer));
            } else {
                if (constant) {
                    fail("Expected initializer for const declaration");
                    return std::nullopt;
                }
                declaration.children.push_back(AstNode{.kind = AstKind::Literal,
                                                       .text = "undefined",
                                                       .literalType = TokenType::KeywordUndefined,
                                                       .line = name.line,
                                                       .column = name.column,
                                                       .children = {}});
            }
            consumeOptionalSemicolon();
            return declaration;
        }

        if (match(TokenType::KeywordReturn)) {
            const auto keyword = previous();
            auto expression = parseExpression();
            if (!expression.has_value()) {
                return std::nullopt;
            }
            consumeOptionalSemicolon();
            AstNode result{.kind = AstKind::ReturnStatement,
                           .text = "return",
                           .line = keyword.line,
                           .column = keyword.column,
                           .children = {}};
            result.children.push_back(std::move(*expression));
            return result;
        }

        auto expression = parseExpression();
        if (!expression.has_value()) {
            return std::nullopt;
        }
        consumeOptionalSemicolon();
        AstNode statement{.kind = AstKind::ExpressionStatement,
                          .text = "expression",
                          .line = expression->line,
                          .column = expression->column,
                          .children = {}};
        statement.children.push_back(std::move(*expression));
        return statement;
    }

    std::optional<AstNode> parseExpression() {
        return parseAssignment();
    }

    std::optional<AstNode> parseAssignment() {
        auto left = parseAdditive();
        if (!left.has_value()) {
            return std::nullopt;
        }

        if (!matchSymbol("=")) {
            return left;
        }

        const auto equals = previous();
        auto value = parseAssignment();
        if (!value.has_value()) {
            return std::nullopt;
        }

        if (left->kind == AstKind::Identifier) {
            AstNode assignment{.kind = AstKind::AssignmentExpression,
                               .text = left->text,
                               .line = equals.line,
                               .column = equals.column,
                               .children = {}};
            assignment.children.push_back(std::move(*value));
            return assignment;
        }

        if (left->kind == AstKind::PropertyAccess) {
            AstNode assignment{.kind = AstKind::PropertyAssignmentExpression,
                               .text = left->text,
                               .line = equals.line,
                               .column = equals.column,
                               .children = {}};
            assignment.children.push_back(std::move(left->children.front()));
            assignment.children.push_back(std::move(*value));
            return assignment;
        }

        fail("Invalid assignment target");
        return std::nullopt;
    }

    std::optional<AstNode> parseAdditive() {
        auto expression = parseMultiplicative();
        if (!expression.has_value()) {
            return std::nullopt;
        }

        while (matchSymbol("+") || matchSymbol("-")) {
            const auto operatorToken = previous();
            auto right = parseMultiplicative();
            if (!right.has_value()) {
                return std::nullopt;
            }
            AstNode binary{.kind = AstKind::BinaryExpression,
                           .text = operatorToken.lexeme,
                           .line = operatorToken.line,
                           .column = operatorToken.column,
                           .children = {}};
            binary.children.push_back(std::move(*expression));
            binary.children.push_back(std::move(*right));
            expression = std::move(binary);
        }
        return expression;
    }

    std::optional<AstNode> parseMultiplicative() {
        auto expression = parseUnary();
        if (!expression.has_value()) {
            return std::nullopt;
        }

        while (matchSymbol("*") || matchSymbol("/")) {
            const auto operatorToken = previous();
            auto right = parseUnary();
            if (!right.has_value()) {
                return std::nullopt;
            }
            AstNode binary{.kind = AstKind::BinaryExpression,
                           .text = operatorToken.lexeme,
                           .line = operatorToken.line,
                           .column = operatorToken.column,
                           .children = {}};
            binary.children.push_back(std::move(*expression));
            binary.children.push_back(std::move(*right));
            expression = std::move(binary);
        }
        return expression;
    }

    std::optional<AstNode> parseUnary() {
        if (matchSymbol("-")) {
            const auto operatorToken = previous();
            auto operand = parseUnary();
            if (!operand.has_value()) {
                return std::nullopt;
            }
            AstNode unary{.kind = AstKind::UnaryExpression,
                          .text = "-",
                          .line = operatorToken.line,
                          .column = operatorToken.column,
                          .children = {}};
            unary.children.push_back(std::move(*operand));
            return unary;
        }
        return parseMember();
    }

    std::optional<AstNode> parseMember() {
        auto expression = parsePrimary();
        if (!expression.has_value()) {
            return std::nullopt;
        }

        while (matchSymbol(".")) {
            if (!check(TokenType::Identifier)) {
                fail("Expected property name after '.'");
                return std::nullopt;
            }
            const auto name = advance();
            AstNode access{.kind = AstKind::PropertyAccess,
                           .text = name.lexeme,
                           .line = name.line,
                           .column = name.column,
                           .children = {}};
            access.children.push_back(std::move(*expression));
            expression = std::move(access);
        }
        return expression;
    }

    std::optional<AstNode> parsePrimary() {
        if (match(TokenType::Number) || match(TokenType::String) || match(TokenType::KeywordTrue) ||
            match(TokenType::KeywordFalse) || match(TokenType::KeywordNull) || match(TokenType::KeywordUndefined)) {
            const auto token = previous();
            return AstNode{.kind = AstKind::Literal,
                           .text = token.lexeme,
                           .literalType = token.type,
                           .line = token.line,
                           .column = token.column,
                           .children = {}};
        }

        if (match(TokenType::Identifier)) {
            const auto token = previous();
            return AstNode{.kind = AstKind::Identifier,
                           .text = token.lexeme,
                           .line = token.line,
                           .column = token.column,
                           .children = {}};
        }

        if (matchSymbol("(")) {
            auto expression = parseExpression();
            if (!expression.has_value()) {
                return std::nullopt;
            }
            if (!matchSymbol(")")) {
                fail("Expected ')' after expression");
                return std::nullopt;
            }
            return expression;
        }

        if (matchSymbol("{")) {
            AstNode object{.kind = AstKind::ObjectLiteral,
                           .text = "object",
                           .line = previous().line,
                           .column = previous().column,
                           .children = {}};
            if (!checkSymbol("}")) {
                do {
                    if (!check(TokenType::Identifier) && !check(TokenType::String)) {
                        fail("Expected object property name");
                        return std::nullopt;
                    }
                    const auto name = advance();
                    if (!matchSymbol(":")) {
                        fail("Expected ':' after object property name");
                        return std::nullopt;
                    }
                    auto value = parseExpression();
                    if (!value.has_value()) {
                        return std::nullopt;
                    }
                    AstNode property{.kind = AstKind::Property,
                                     .text = name.lexeme,
                                     .line = name.line,
                                     .column = name.column,
                                     .children = {}};
                    property.children.push_back(std::move(*value));
                    object.children.push_back(std::move(property));
                } while (matchSymbol(","));
            }
            if (!matchSymbol("}")) {
                fail("Expected '}' after object literal");
                return std::nullopt;
            }
            return object;
        }

        fail("Expected expression");
        return std::nullopt;
    }

    bool match(TokenType type) {
        if (!check(type)) {
            return false;
        }
        advance();
        return true;
    }

    bool matchSymbol(const std::string& symbol) {
        if (!checkSymbol(symbol)) {
            return false;
        }
        advance();
        return true;
    }

    bool check(TokenType type) const {
        return !isAtEnd() && peek().type == type;
    }

    bool checkSymbol(const std::string& symbol) const {
        return !isAtEnd() && peek().type == TokenType::Symbol && peek().lexeme == symbol;
    }

    const Token& advance() {
        if (!isAtEnd()) {
            ++current_;
        }
        return previous();
    }

    const Token& peek() const {
        return tokens_[current_];
    }

    const Token& previous() const {
        return tokens_[current_ - 1];
    }

    bool isAtEnd() const {
        return current_ >= tokens_.size() || tokens_[current_].type == TokenType::EndOfFile;
    }

    void consumeOptionalSemicolon() {
        (void)matchSymbol(";");
    }

    void fail(const std::string& message) {
        const auto token = isAtEnd() ? tokens_.back() : peek();
        error_ = message + " at line " + std::to_string(token.line) + ", column " + std::to_string(token.column);
    }

    const std::vector<Token>& tokens_;
    std::size_t current_{0};
    std::string error_;
};

} // namespace

ParseResult Parser::parseProgram(const std::vector<Token>& tokens) const {
    if (tokens.empty()) {
        return {.ok = false, .nodes = {}, .error = "Parser received an empty token stream"};
    }

    for (const auto& token : tokens) {
        if (token.type == TokenType::Invalid) {
            return {.ok = false,
                    .nodes = {},
                    .error = "Invalid token '" + token.lexeme + "' at line " + std::to_string(token.line) +
                             ", column " + std::to_string(token.column)};
        }
    }

    ParserImpl parser(tokens);
    return parser.parse();
}

} // namespace jsengine::compiler
