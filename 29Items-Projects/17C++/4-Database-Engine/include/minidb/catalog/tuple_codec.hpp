#pragma once

#include <cstddef>
#include <string>
#include <vector>

#include "minidb/catalog/schema.hpp"
#include "minidb/common/types.hpp"

/// @file tuple_codec.hpp
/// Serializes a row of `Value`s to the compact on-page byte layout and back,
/// driven by a `Schema`. Layout: a null bitmap (1 bit per column) followed by
/// the non-null column values in schema order (fixed-width inline; VARCHAR as a
/// u16 length prefix + bytes).

namespace minidb {

struct TupleCodec {
  /// Encodes @p row (must have schema.column_count() values) to bytes.
  static std::string Serialize(const Schema& schema,
                               const std::vector<Value>& row);

  /// Decodes @p len bytes at @p data into a row of values per @p schema.
  static std::vector<Value> Deserialize(const Schema& schema, const char* data,
                                        std::size_t len);
};

}  // namespace minidb
