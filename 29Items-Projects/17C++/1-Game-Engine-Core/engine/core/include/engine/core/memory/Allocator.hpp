#pragma once

#include "engine/core/Types.hpp"

/// @file Allocator.hpp
/// @brief Allocator interface + alignment helpers.
///
/// The engine avoids the general heap in hot paths. Frame-scoped data uses a
/// LinearAllocator (bump arena, reset per frame); fixed-size objects use a
/// PoolAllocator. Both satisfy this minimal interface.

namespace engine::memory {

/// Round `value` up to the next multiple of `alignment` (must be a power of two).
[[nodiscard]] constexpr usize alignUp(usize value, usize alignment) noexcept {
    return (value + (alignment - 1)) & ~(alignment - 1);
}

[[nodiscard]] inline bool isPowerOfTwo(usize x) noexcept {
    return x != 0 && (x & (x - 1)) == 0;
}

class IAllocator {
public:
    virtual ~IAllocator() = default;

    /// Allocate `size` bytes with `alignment`. Returns nullptr on exhaustion.
    [[nodiscard]] virtual void* allocate(usize size, usize alignment = alignof(std::max_align_t)) = 0;

    /// Free a previous allocation. May be a no-op for arena-style allocators.
    virtual void deallocate(void* ptr) = 0;

    /// Bytes currently handed out.
    [[nodiscard]] virtual usize bytesInUse() const noexcept = 0;
};

} // namespace engine::memory
