#pragma once

#include "engine/core/Assert.hpp"
#include "engine/core/Types.hpp"
#include "engine/core/memory/Allocator.hpp"

/// @file LinearAllocator.hpp
/// @brief Bump-pointer arena. O(1) allocate, bulk free via reset().
///
/// Ideal for per-frame scratch data: allocate freely during the frame, then call
/// reset() once at frame end. Individual deallocate() is intentionally a no-op.

namespace engine::memory {

class LinearAllocator final : public IAllocator {
public:
    /// Wraps a caller-owned buffer. The allocator does not own `buffer`.
    LinearAllocator(void* buffer, usize capacity)
        : base_(static_cast<u8*>(buffer)), capacity_(capacity) {
        ENGINE_ASSERT(buffer != nullptr, "LinearAllocator needs a non-null buffer");
    }

    [[nodiscard]] void* allocate(usize size, usize alignment = alignof(std::max_align_t)) override {
        ENGINE_ASSERT(isPowerOfTwo(alignment), "alignment must be a power of two");
        const usize alignedOffset = alignUp(offset_, alignment);
        if (alignedOffset + size > capacity_) {
            return nullptr; // arena exhausted — caller decides how to handle
        }
        void* ptr = base_ + alignedOffset;
        offset_   = alignedOffset + size;
        return ptr;
    }

    /// No-op: linear allocators free in bulk via reset().
    void deallocate(void* /*ptr*/) override {}

    /// Rewind the arena. Does not run destructors — POD/trivially-destructible only.
    void reset() noexcept { offset_ = 0; }

    [[nodiscard]] usize bytesInUse() const noexcept override { return offset_; }
    [[nodiscard]] usize capacity() const noexcept { return capacity_; }

private:
    u8*   base_   = nullptr;
    usize capacity_ = 0;
    usize offset_ = 0;
};

} // namespace engine::memory
