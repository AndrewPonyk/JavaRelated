#include "minidb/catalog/tuple_codec.hpp"

#include <cstdint>
#include <cstring>

#include "minidb/common/byte_io.hpp"

namespace minidb {
namespace {

void AppendU16(std::string& buf, std::uint16_t v) {
  char tmp[2];
  StoreU16(tmp, v);
  buf.append(tmp, sizeof(tmp));
}
void AppendI32(std::string& buf, std::int32_t v) {
  char tmp[4];
  StoreI32(tmp, v);
  buf.append(tmp, sizeof(tmp));
}
void AppendI64(std::string& buf, std::int64_t v) {
  char tmp[8];
  StoreI64(tmp, v);
  buf.append(tmp, sizeof(tmp));
}

}  // namespace

std::string TupleCodec::Serialize(const Schema& schema,
                                  const std::vector<Value>& row) {
  const std::size_t n = schema.column_count();
  const std::size_t bitmap_bytes = (n + 7) / 8;

  std::string out(bitmap_bytes, '\0');
  for (std::size_t i = 0; i < n; ++i) {
    const Value& v = i < row.size() ? row[i] : Value();
    if (v.is_null()) {
      out[i / 8] = static_cast<char>(out[i / 8] | (1 << (i % 8)));
      continue;
    }
    switch (schema.column(i).type) {
      case TypeId::kBoolean:
        out.push_back(v.AsInt64().value_or(0) != 0 ? '\1' : '\0');
        break;
      case TypeId::kInteger:
        AppendI32(out, static_cast<std::int32_t>(v.AsInt64().value_or(0)));
        break;
      case TypeId::kBigInt:
        AppendI64(out, v.AsInt64().value_or(0));
        break;
      case TypeId::kVarchar: {
        const std::string s = v.AsString().value_or("");
        AppendU16(out, static_cast<std::uint16_t>(s.size()));
        out.append(s);
        break;
      }
      case TypeId::kInvalid:
        break;
    }
  }
  return out;
}

std::vector<Value> TupleCodec::Deserialize(const Schema& schema,
                                           const char* data, std::size_t len) {
  const std::size_t n = schema.column_count();
  const std::size_t bitmap_bytes = (n + 7) / 8;
  std::vector<Value> row(n);

  std::size_t pos = bitmap_bytes;
  if (len < bitmap_bytes) return row;  // corrupt/short: all NULL

  for (std::size_t i = 0; i < n; ++i) {
    const bool is_null =
        (static_cast<unsigned char>(data[i / 8]) >> (i % 8)) & 1u;
    if (is_null) {
      row[i] = Value();
      continue;
    }
    switch (schema.column(i).type) {
      case TypeId::kBoolean:
        if (pos + 1 > len) return row;
        row[i] = Value(data[pos] != 0);
        pos += 1;
        break;
      case TypeId::kInteger:
        if (pos + 4 > len) return row;
        row[i] = Value(static_cast<std::int32_t>(LoadI32(data + pos)));
        pos += 4;
        break;
      case TypeId::kBigInt:
        if (pos + 8 > len) return row;
        row[i] = Value(LoadI64(data + pos));
        pos += 8;
        break;
      case TypeId::kVarchar: {
        if (pos + 2 > len) return row;
        const std::uint16_t slen = LoadU16(data + pos);
        pos += 2;
        if (pos + slen > len) return row;
        row[i] = Value(std::string(data + pos, slen));
        pos += slen;
        break;
      }
      case TypeId::kInvalid:
        row[i] = Value();
        break;
    }
  }
  return row;
}

}  // namespace minidb
