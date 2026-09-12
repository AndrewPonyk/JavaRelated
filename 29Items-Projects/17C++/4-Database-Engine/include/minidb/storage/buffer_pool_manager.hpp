#pragma once

#include <cstddef>
#include <list>
#include <mutex>
#include <unordered_map>
#include <vector>

#include "minidb/common/status.hpp"
#include "minidb/storage/disk_manager.hpp"
#include "minidb/storage/lru_replacer.hpp"
#include "minidb/storage/page.hpp"

/// @file buffer_pool_manager.hpp
/// A fixed-size pool of in-memory frames caching disk pages. On a miss it reads
/// through the DiskManager, evicting a victim chosen by the LRUReplacer
/// (writing it back first if dirty). This is the "LRU cache for buffer pool
/// management" at the heart of the engine.
///
/// Borrowing protocol: every FetchPage/NewPage pins the page and returns a
/// borrowed `Page*`. The caller MUST call UnpinPage exactly once when done.

namespace minidb {

class BufferPoolManager {
 public:
  BufferPoolManager(std::size_t pool_size, DiskManager* disk_manager);
  ~BufferPoolManager() = default;

  BufferPoolManager(const BufferPoolManager&) = delete;
  BufferPoolManager& operator=(const BufferPoolManager&) = delete;

  /// Fetches @p page_id into the pool (pinning it). Returns nullptr if no frame
  /// is free and every frame is pinned.
  Page* FetchPage(page_id_t page_id);

  /// Allocates a fresh page on disk and pins it in the pool. Writes the new
  /// page id to @p page_id. Returns nullptr if the pool is full.
  Page* NewPage(page_id_t* page_id);

  /// Decrements the pin count of @p page_id. Marks it dirty if @p is_dirty.
  /// Returns false if the page is not resident or already at pin count 0.
  bool UnpinPage(page_id_t page_id, bool is_dirty);

  /// Writes @p page_id back to disk (regardless of dirty flag) and clears it.
  bool FlushPage(page_id_t page_id);

  /// Flushes every resident dirty page and Syncs the underlying file.
  Status FlushAll();

  std::size_t pool_size() const { return pool_size_; }

 private:
  /// Finds a free frame, or evicts an LRU victim into one. Returns false if the
  /// pool is full of pinned pages. On success @p out_frame holds a ready frame.
  bool GetVictimFrame(frame_id_t* out_frame);

  const std::size_t pool_size_;
  DiskManager* disk_manager_;

  std::vector<Page> frames_;
  std::unordered_map<page_id_t, frame_id_t> page_table_;
  std::list<frame_id_t> free_list_;
  LRUReplacer replacer_;
  std::mutex latch_;
};

}  // namespace minidb
