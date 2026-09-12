#pragma once

#include <cstddef>
#include <cstdint>

/// @file Types.hpp
/// @brief Fixed-width primitive aliases used throughout the engine.

namespace engine {

using u8  = std::uint8_t;
using u16 = std::uint16_t;
using u32 = std::uint32_t;
using u64 = std::uint64_t;

using i8  = std::int8_t;
using i16 = std::int16_t;
using i32 = std::int32_t;
using i64 = std::int64_t;

using f32 = float;
using f64 = double;

using usize = std::size_t;
using uptr  = std::uintptr_t;

/// Cache line size assumed for alignment of hot, per-thread/per-frame data.
inline constexpr usize kCacheLineSize = 64;

} // namespace engine
