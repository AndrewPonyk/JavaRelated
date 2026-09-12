#pragma once

#include <cstdint>
#include <string>
#include <variant>
#include <vector>

#include "minidb/common/types.hpp"

/// @file ast.hpp
/// Abstract syntax tree for the supported SQL subset:
///   CREATE TABLE <name> (<col> <type> [, ...])
///   INSERT INTO <name> [(<cols>)] VALUES (<vals>) [, (<vals>) ...]
///   SELECT <cols>|* FROM <name> [WHERE <col> <op> <literal>]

namespace minidb {

enum class CompareOp { kEq, kNe, kLt, kLe, kGt, kGe };

const char* CompareOpToString(CompareOp op);

struct ColumnDef {
  std::string name;
  TypeId type = TypeId::kInvalid;
  std::uint32_t length = 0;  ///< VARCHAR length, else 0
};

struct CreateTableStatement {
  std::string table;
  std::vector<ColumnDef> columns;
};

struct InsertStatement {
  std::string table;
  std::vector<std::string> columns;      ///< empty => all columns, in order
  std::vector<std::vector<Value>> rows;  ///< one inner vector per VALUES tuple
};

/// A single, simple predicate: `column <op> literal`. v1 supports at most one;
/// compound AND/OR predicates are future work (Phase 3).
struct Predicate {
  bool present = false;
  std::string column;
  CompareOp op = CompareOp::kEq;
  Value literal;
};

struct SelectStatement {
  std::vector<std::string> columns;  ///< empty or {"*"} => SELECT *
  std::string table;
  Predicate where;
  bool explain = false;  ///< EXPLAIN <select>: report the plan, don't run it
};

using Statement =
    std::variant<CreateTableStatement, InsertStatement, SelectStatement>;

}  // namespace minidb
