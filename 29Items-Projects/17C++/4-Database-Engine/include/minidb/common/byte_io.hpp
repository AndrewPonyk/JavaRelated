#pragma once

#include <cstdint>
#include <cstring>

/// @file byte_io.hpp
/// Alignment-safe fixed-width load/store helpers for reading and writing packed
/// fields inside raw page bytes. Using `memcpy` avoids the undefined behavior
/// of reinterpreting an arbitrary `char*` offset as an aligned integer pointer.

namespace minidb {

inline std::uint16_t LoadU16(const char* p) {
  std::uint16_t v;
  std::memcpy(&v, p, sizeof(v));
  return v;
}
inline void StoreU16(char* p, std::uint16_t v) {
  std::memcpy(p, &v, sizeof(v));
}

inline std::uint32_t LoadU32(const char* p) {
  std::uint32_t v;
  std::memcpy(&v, p, sizeof(v));
  return v;
}
inline void StoreU32(char* p, std::uint32_t v) {
  std::memcpy(p, &v, sizeof(v));
}

inline std::int32_t LoadI32(const char* p) {
  std::int32_t v;
  std::memcpy(&v, p, sizeof(v));
  return v;
}
inline void StoreI32(char* p, std::int32_t v) {
  std::memcpy(p, &v, sizeof(v));
}

inline std::int64_t LoadI64(const char* p) {
  std::int64_t v;
  std::memcpy(&v, p, sizeof(v));
  return v;
}
inline void StoreI64(char* p, std::int64_t v) {
  std::memcpy(p, &v, sizeof(v));
}

}  // namespace minidb
