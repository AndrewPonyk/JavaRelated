#include "minidb/common/types.hpp"

namespace minidb {

const char* TypeIdToString(TypeId type) {
  switch (type) {
    case TypeId::kInvalid:
      return "INVALID";
    case TypeId::kBoolean:
      return "BOOLEAN";
    case TypeId::kInteger:
      return "INTEGER";
    case TypeId::kBigInt:
      return "BIGINT";
    case TypeId::kVarchar:
      return "VARCHAR";
  }
  return "INVALID";
}

std::size_t TypeSize(TypeId type) {
  switch (type) {
    case TypeId::kBoolean:
      return 1;
    case TypeId::kInteger:
      return 4;
    case TypeId::kBigInt:
      return 8;
    case TypeId::kVarchar:
      return 0;  // variable-width
    case TypeId::kInvalid:
      return 0;
  }
  return 0;
}

TypeId Value::type() const {
  if (std::holds_alternative<bool>(data_)) return TypeId::kBoolean;
  if (std::holds_alternative<std::int32_t>(data_)) return TypeId::kInteger;
  if (std::holds_alternative<std::int64_t>(data_)) return TypeId::kBigInt;
  if (std::holds_alternative<std::string>(data_)) return TypeId::kVarchar;
  return TypeId::kInvalid;  // NULL / monostate
}

std::optional<std::int64_t> Value::AsInt64() const {
  if (auto* b = std::get_if<bool>(&data_)) return *b ? 1 : 0;
  if (auto* i = std::get_if<std::int32_t>(&data_))
    return static_cast<std::int64_t>(*i);
  if (auto* l = std::get_if<std::int64_t>(&data_)) return *l;
  return std::nullopt;
}

std::optional<std::string> Value::AsString() const {
  if (auto* s = std::get_if<std::string>(&data_)) return *s;
  return std::nullopt;
}

std::string Value::ToString() const {
  if (is_null()) return "NULL";
  if (auto* b = std::get_if<bool>(&data_)) return *b ? "true" : "false";
  if (auto* i = std::get_if<std::int32_t>(&data_)) return std::to_string(*i);
  if (auto* l = std::get_if<std::int64_t>(&data_)) return std::to_string(*l);
  if (auto* s = std::get_if<std::string>(&data_)) return *s;
  return "NULL";
}

int CompareValues(const Value& lhs, const Value& rhs) {
  // NULL ordering: null == null, null < anything else.
  if (lhs.is_null() || rhs.is_null()) {
    if (lhs.is_null() && rhs.is_null()) return 0;
    return lhs.is_null() ? -1 : 1;
  }

  // Numeric comparison when both sides are numeric.
  auto la = lhs.AsInt64();
  auto ra = rhs.AsInt64();
  if (la && ra) {
    if (*la < *ra) return -1;
    if (*la > *ra) return 1;
    return 0;
  }

  // Fall back to lexical comparison of the rendered representations.
  const std::string ls = lhs.ToString();
  const std::string rs = rhs.ToString();
  return ls.compare(rs) < 0 ? -1 : (ls == rs ? 0 : 1);
}

}  // namespace minidb
