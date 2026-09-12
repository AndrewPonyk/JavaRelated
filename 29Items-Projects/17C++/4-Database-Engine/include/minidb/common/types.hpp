#pragma once

#include <compare>
#include <cstdint>
#include <optional>
#include <string>
#include <variant>

#include "minidb/common/config.hpp"

/// @file types.hpp
/// The SQL type system surface: column types, record identifiers, and the
/// dynamically-typed `Value` used by the parser and executor.

namespace minidb {

/// Supported column types in the SQL subset.
enum class TypeId : std::uint8_t {
  kInvalid = 0,
  kBoolean,
  kInteger,  ///< 32-bit signed
  kBigInt,   ///< 64-bit signed
  kVarchar,  ///< variable-length UTF-8 text
};

const char* TypeIdToString(TypeId type);

/// Fixed serialized width in bytes for fixed-width types; 0 for variable-width
/// (kVarchar) and kInvalid.
std::size_t TypeSize(TypeId type);

/// Record identifier: the physical address of a tuple (which page, which slot).
struct RID {
  page_id_t page_id = INVALID_PAGE_ID;
  slot_id_t slot = 0;

  bool operator==(const RID&) const = default;
  auto operator<=>(const RID&) const = default;
};

/// A dynamically-typed SQL value. A default-constructed Value is SQL NULL.
class Value {
 public:
  Value() = default;  ///< SQL NULL
  explicit Value(bool b) : data_(b) {}
  explicit Value(std::int32_t v) : data_(v) {}
  explicit Value(std::int64_t v) : data_(v) {}
  explicit Value(std::string s) : data_(std::move(s)) {}

  bool is_null() const { return std::holds_alternative<std::monostate>(data_); }
  TypeId type() const;

  /// Numeric view of the value (integers/bool). nullopt for strings/null.
  std::optional<std::int64_t> AsInt64() const;
  /// String view of the value. nullopt for non-string/null.
  std::optional<std::string> AsString() const;

  std::string ToString() const;

 private:
  std::variant<std::monostate, bool, std::int32_t, std::int64_t, std::string>
      data_;
};

/// Three-way comparison of two values. Returns <0, 0, or >0.
/// NULL sorts before any non-null. Numeric values compare numerically across
/// kInteger/kBigInt/kBoolean; otherwise comparison is by string content.
int CompareValues(const Value& lhs, const Value& rhs);

}  // namespace minidb
