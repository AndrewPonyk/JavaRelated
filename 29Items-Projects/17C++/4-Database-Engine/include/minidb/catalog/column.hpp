#pragma once

#include <cstdint>
#include <string>

#include "minidb/common/types.hpp"

/// @file column.hpp
/// A single column definition: name, type, and (for VARCHAR) max length.

namespace minidb {

struct Column {
  std::string name;
  TypeId type = TypeId::kInvalid;
  std::uint32_t length = 0;  ///< Max bytes for kVarchar; ignored otherwise.

  /// Serialized width of one value of this column (max width for varchar).
  std::size_t fixed_size() const {
    return type == TypeId::kVarchar ? length : TypeSize(type);
  }
};

}  // namespace minidb
