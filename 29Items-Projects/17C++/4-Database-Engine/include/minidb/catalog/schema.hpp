#pragma once

#include <cstddef>
#include <optional>
#include <string_view>
#include <vector>

#include "minidb/catalog/column.hpp"

/// @file schema.hpp
/// An ordered set of columns describing a table's tuple layout.

namespace minidb {

class Schema {
 public:
  Schema() = default;
  explicit Schema(std::vector<Column> columns) : columns_(std::move(columns)) {}

  const std::vector<Column>& columns() const { return columns_; }
  std::size_t column_count() const { return columns_.size(); }
  const Column& column(std::size_t i) const { return columns_.at(i); }

  /// Position of the column named @p name, or nullopt if there is no such
  /// column. Comparison is case-sensitive (the lexer lowercases identifiers).
  std::optional<std::size_t> GetColumnIndex(std::string_view name) const {
    for (std::size_t i = 0; i < columns_.size(); ++i) {
      if (columns_[i].name == name) return i;
    }
    return std::nullopt;
  }

 private:
  std::vector<Column> columns_;
};

}  // namespace minidb
