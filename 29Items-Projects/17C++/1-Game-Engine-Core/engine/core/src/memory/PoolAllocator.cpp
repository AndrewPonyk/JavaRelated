#include "engine/core/memory/PoolAllocator.hpp"

#include "engine/core/Assert.hpp"

#include <algorithm>
#include <new>
#include <utility>

/// @file PoolAllocator.cpp
/// @brief Fixed-size block pool with an intrusive singly-linked free list. Each
/// free block stores the pointer to the next free block in its first bytes, so the
/// free list costs no extra memory.

namespace engine::memory {

PoolAllocator::PoolAllocator(usize blockSize, usize blockCount, usize alignment)
    : blockCount_(blockCount), alignment_(alignment) {
    ENGINE_ASSERT(isPowerOfTwo(alignment), "pool alignment must be a power of two");
    ENGINE_ASSERT(blockCount > 0, "pool needs at least one block");

    // Each block must be large enough to hold a free-list pointer and aligned.
    blockSize_ = alignUp(std::max(blockSize, sizeof(void*)), alignment);

    const usize totalSize = blockSize_ * blockCount_;
    buffer_ = static_cast<u8*>(::operator new(totalSize, std::align_val_t{alignment_}));
    buildFreeList();
}

PoolAllocator::~PoolAllocator() {
    if (buffer_ != nullptr) {
        ::operator delete(buffer_, std::align_val_t{alignment_});
    }
}

PoolAllocator::PoolAllocator(PoolAllocator&& other) noexcept
    : buffer_(other.buffer_), freeHead_(other.freeHead_), blockSize_(other.blockSize_),
      blockCount_(other.blockCount_), freeCount_(other.freeCount_),
      alignment_(other.alignment_) {
    other.buffer_   = nullptr;
    other.freeHead_ = nullptr;
    other.freeCount_ = 0;
}

PoolAllocator& PoolAllocator::operator=(PoolAllocator&& other) noexcept {
    if (this != &other) {
        if (buffer_ != nullptr) {
            ::operator delete(buffer_, std::align_val_t{alignment_});
        }
        buffer_     = std::exchange(other.buffer_, nullptr);
        freeHead_   = std::exchange(other.freeHead_, nullptr);
        blockSize_  = other.blockSize_;
        blockCount_ = other.blockCount_;
        freeCount_  = std::exchange(other.freeCount_, 0);
        alignment_  = other.alignment_;
    }
    return *this;
}

void PoolAllocator::buildFreeList() noexcept {
    freeHead_ = buffer_;
    for (usize i = 0; i < blockCount_ - 1; ++i) {
        void* current = buffer_ + i * blockSize_;
        void* next    = buffer_ + (i + 1) * blockSize_;
        *reinterpret_cast<void**>(current) = next;
    }
    *reinterpret_cast<void**>(buffer_ + (blockCount_ - 1) * blockSize_) = nullptr;
    freeCount_ = blockCount_;
}

void* PoolAllocator::allocate(usize size, usize alignment) {
    ENGINE_ASSERT(size <= blockSize_, "allocation {} exceeds block size {}", size, blockSize_);
    ENGINE_ASSERT(alignment <= alignment_, "requested alignment exceeds pool alignment");
    if (freeHead_ == nullptr) {
        return nullptr; // pool exhausted
    }
    void* block = freeHead_;
    freeHead_   = *reinterpret_cast<void**>(freeHead_);
    --freeCount_;
    return block;
}

void PoolAllocator::deallocate(void* ptr) {
    if (ptr == nullptr) {
        return;
    }
    ENGINE_ASSERT(ptr >= buffer_ && ptr < buffer_ + blockSize_ * blockCount_,
                  "deallocate of pointer not owned by this pool");
    *reinterpret_cast<void**>(ptr) = freeHead_;
    freeHead_                      = ptr;
    ++freeCount_;
}

usize PoolAllocator::bytesInUse() const noexcept {
    return (blockCount_ - freeCount_) * blockSize_;
}

} // namespace engine::memory
