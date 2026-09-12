#pragma once

#include "engine/core/Types.hpp"
#include "engine/core/memory/Allocator.hpp"

/// @file PoolAllocator.hpp
/// @brief Fixed-size block allocator with an intrusive free list.
///
/// O(1) allocate/deallocate of equally sized blocks with no fragmentation — the
/// right tool for many same-type objects (e.g., component nodes, particles).
/// Implementation in PoolAllocator.cpp.

namespace engine::memory {

class PoolAllocator final : public IAllocator {
public:
    /// @param blockSize  size of each block (bytes); rounded up to hold a pointer.
    /// @param blockCount number of blocks to reserve.
    /// @param alignment  alignment for each block (power of two).
    PoolAllocator(usize blockSize, usize blockCount,
                  usize alignment = alignof(std::max_align_t));
    ~PoolAllocator() override;

    PoolAllocator(const PoolAllocator&)            = delete;
    PoolAllocator& operator=(const PoolAllocator&) = delete;
    PoolAllocator(PoolAllocator&&) noexcept;
    PoolAllocator& operator=(PoolAllocator&&) noexcept;

    /// Returns one block. `size` must be <= blockSize. Returns nullptr if full.
    [[nodiscard]] void* allocate(usize size, usize alignment = alignof(std::max_align_t)) override;

    /// Returns a block to the free list. `ptr` must come from this pool.
    void deallocate(void* ptr) override;

    [[nodiscard]] usize bytesInUse() const noexcept override;
    [[nodiscard]] usize blockSize() const noexcept { return blockSize_; }
    [[nodiscard]] usize capacityBlocks() const noexcept { return blockCount_; }
    [[nodiscard]] usize freeBlocks() const noexcept { return freeCount_; }

private:
    void buildFreeList() noexcept;

    u8*   buffer_     = nullptr; // owned backing store
    void* freeHead_   = nullptr; // head of the intrusive free list
    usize blockSize_  = 0;
    usize blockCount_ = 0;
    usize freeCount_  = 0;
    usize alignment_  = 0;
};

} // namespace engine::memory
