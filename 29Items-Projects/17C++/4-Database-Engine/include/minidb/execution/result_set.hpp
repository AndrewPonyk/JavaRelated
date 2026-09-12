#pragma once

#include <cstddef>
#include <string>
#include <vector>

#include "minidb/common/types.hpp"

/// @file result_set.hpp
/// The materialized output of executing a statement: column headers plus rows
/// for queries, or an affected-row count and message for DML/DDL.

namespace minidb {

struct ResultSet {
  std::vector<std::string> columns;      ///< column headers (empty for DDL/DML)
  std::vector<std::vector<Value>> rows;  ///< result tuples (SELECT)
  std::size_t affected_rows = 0;         ///< rows changed (INSERT) or 0
  std::string message;                   ///< human-readable summary / notes

  bool is_query() const { return !columns.empty(); }
};

}  // namespace minidb
