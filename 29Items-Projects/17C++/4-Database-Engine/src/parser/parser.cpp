#include "minidb/parser/parser.hpp"

#include <cerrno>
#include <cstdlib>
#include <optional>
#include <string>
#include <utility>

namespace minidb {
namespace {

std::optional<long long> ToInt(const std::string& s) {
  if (s.empty()) return std::nullopt;
  errno = 0;
  char* end = nullptr;
  const long long v = std::strtoll(s.c_str(), &end, 10);
  if (errno != 0 || end != s.c_str() + s.size()) return std::nullopt;
  return v;
}

}  // namespace

const char* CompareOpToString(CompareOp op) {
  switch (op) {
    case CompareOp::kEq:
      return "=";
    case CompareOp::kNe:
      return "!=";
    case CompareOp::kLt:
      return "<";
    case CompareOp::kLe:
      return "<=";
    case CompareOp::kGt:
      return ">";
    case CompareOp::kGe:
      return ">=";
  }
  return "?";
}

const Token& Parser::Advance() {
  if (!AtEnd()) ++pos_;
  return Previous();
}

bool Parser::Match(TokenType type) {
  if (Check(type)) {
    Advance();
    return true;
  }
  return false;
}

StatusOr<Statement> Parser::Parse() {
  if (Match(TokenType::kCreate)) return ParseCreateTable();
  if (Match(TokenType::kInsert)) return ParseInsert();
  if (Match(TokenType::kSelect)) return ParseSelect();
  if (Match(TokenType::kExplain)) {  // EXPLAIN <select>
    if (!Match(TokenType::kSelect)) {
      return Status::SyntaxError("EXPLAIN must be followed by a SELECT");
    }
    auto stmt = ParseSelect();
    if (!stmt.ok()) return stmt.status();
    std::get<SelectStatement>(stmt.value()).explain = true;
    return stmt;
  }
  return Status::SyntaxError("expected SELECT, INSERT, CREATE, or EXPLAIN");
}

StatusOr<Statement> Parser::ParseCreateTable() {
  if (!Match(TokenType::kTable)) {
    return Status::SyntaxError("expected TABLE after CREATE");
  }
  if (!Check(TokenType::kIdentifier)) {
    return Status::SyntaxError("expected table name");
  }
  CreateTableStatement stmt;
  stmt.table = Advance().lexeme;

  if (!Match(TokenType::kLParen)) {
    return Status::SyntaxError("expected '(' before column definitions");
  }
  do {
    if (!Check(TokenType::kIdentifier)) {
      return Status::SyntaxError("expected column name");
    }
    ColumnDef col;
    col.name = Advance().lexeme;

    switch (Peek().type) {
      case TokenType::kIntType:
        col.type = TypeId::kInteger;
        Advance();
        break;
      case TokenType::kBigIntType:
        col.type = TypeId::kBigInt;
        Advance();
        break;
      case TokenType::kBoolType:
        col.type = TypeId::kBoolean;
        Advance();
        break;
      case TokenType::kVarcharType: {
        col.type = TypeId::kVarchar;
        col.length = 255;  // default if no length given
        Advance();
        if (Match(TokenType::kLParen)) {
          if (!Check(TokenType::kNumber)) {
            return Status::SyntaxError("expected length inside VARCHAR(...)");
          }
          auto n = ToInt(Advance().lexeme);
          if (!n || *n <= 0) {
            return Status::SyntaxError("invalid VARCHAR length");
          }
          col.length = static_cast<std::uint32_t>(*n);
          if (!Match(TokenType::kRParen)) {
            return Status::SyntaxError("expected ')' after VARCHAR length");
          }
        }
        break;
      }
      default:
        return Status::SyntaxError("expected a column type for column '" +
                                   col.name + "'");
    }
    stmt.columns.push_back(std::move(col));
  } while (Match(TokenType::kComma));

  if (!Match(TokenType::kRParen)) {
    return Status::SyntaxError("expected ')' to close column list");
  }
  Match(TokenType::kSemicolon);  // optional terminator
  return Statement{std::move(stmt)};
}

StatusOr<Statement> Parser::ParseInsert() {
  if (!Match(TokenType::kInto)) {
    return Status::SyntaxError("expected INTO after INSERT");
  }
  if (!Check(TokenType::kIdentifier)) {
    return Status::SyntaxError("expected table name");
  }
  InsertStatement stmt;
  stmt.table = Advance().lexeme;

  if (Match(TokenType::kLParen)) {  // optional explicit column list
    do {
      if (!Check(TokenType::kIdentifier)) {
        return Status::SyntaxError("expected column name");
      }
      stmt.columns.push_back(Advance().lexeme);
    } while (Match(TokenType::kComma));
    if (!Match(TokenType::kRParen)) {
      return Status::SyntaxError("expected ')' after column list");
    }
  }

  if (!Match(TokenType::kValues)) {
    return Status::SyntaxError("expected VALUES");
  }
  do {
    if (!Match(TokenType::kLParen)) {
      return Status::SyntaxError("expected '(' before a value tuple");
    }
    std::vector<Value> row;
    if (!Check(TokenType::kRParen)) {
      do {
        auto v = ParseLiteral();
        if (!v.ok()) return v.status();
        row.push_back(std::move(v).value());
      } while (Match(TokenType::kComma));
    }
    if (!Match(TokenType::kRParen)) {
      return Status::SyntaxError("expected ')' after a value tuple");
    }
    stmt.rows.push_back(std::move(row));
  } while (Match(TokenType::kComma));

  Match(TokenType::kSemicolon);
  return Statement{std::move(stmt)};
}

StatusOr<Statement> Parser::ParseSelect() {
  SelectStatement stmt;
  if (Match(TokenType::kStar)) {
    // empty columns => project all
  } else {
    do {
      if (!Check(TokenType::kIdentifier)) {
        return Status::SyntaxError("expected a column name in the SELECT list");
      }
      stmt.columns.push_back(Advance().lexeme);
    } while (Match(TokenType::kComma));
  }

  if (!Match(TokenType::kFrom)) {
    return Status::SyntaxError("expected FROM");
  }
  if (!Check(TokenType::kIdentifier)) {
    return Status::SyntaxError("expected table name");
  }
  stmt.table = Advance().lexeme;

  if (Match(TokenType::kWhere)) {
    auto where = ParseWhere();
    if (!where.ok()) return where.status();
    stmt.where = std::move(where).value();
  }
  Match(TokenType::kSemicolon);
  return Statement{std::move(stmt)};
}

StatusOr<Predicate> Parser::ParseWhere() {
  Predicate p;
  p.present = true;
  if (!Check(TokenType::kIdentifier)) {
    return Status::SyntaxError("expected a column name after WHERE");
  }
  p.column = Advance().lexeme;

  switch (Peek().type) {
    case TokenType::kEq:
      p.op = CompareOp::kEq;
      break;
    case TokenType::kNe:
      p.op = CompareOp::kNe;
      break;
    case TokenType::kLt:
      p.op = CompareOp::kLt;
      break;
    case TokenType::kLe:
      p.op = CompareOp::kLe;
      break;
    case TokenType::kGt:
      p.op = CompareOp::kGt;
      break;
    case TokenType::kGe:
      p.op = CompareOp::kGe;
      break;
    default:
      return Status::SyntaxError("expected a comparison operator in WHERE");
  }
  Advance();

  auto literal = ParseLiteral();
  if (!literal.ok()) return literal.status();
  p.literal = std::move(literal).value();
  // NOTE: the v1 grammar supports exactly one predicate; AND/OR chaining is
  // future work (docs/PROJECT-PLAN.md, Phase 3).
  return p;
}

StatusOr<Value> Parser::ParseLiteral() {
  const Token& t = Peek();
  switch (t.type) {
    case TokenType::kNumber: {
      Advance();
      auto n = ToInt(t.lexeme);
      if (!n)
        return Status::SyntaxError("invalid integer literal: " + t.lexeme);
      return Value(static_cast<std::int64_t>(*n));
    }
    case TokenType::kString:
      Advance();
      return Value(t.lexeme);
    case TokenType::kTrue:
      Advance();
      return Value(true);
    case TokenType::kFalse:
      Advance();
      return Value(false);
    default:
      return Status::SyntaxError("expected a literal value");
  }
}

}  // namespace minidb
