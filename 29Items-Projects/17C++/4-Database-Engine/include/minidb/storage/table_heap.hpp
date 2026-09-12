#pragma once

#include <cstdint>
#include <string>
#include <utility>
#include <vector>

#include "minidb/common/status.hpp"
#include "minidb/common/types.hpp"
#include "minidb/storage/buffer_pool_manager.hpp"

/// @file table_heap.hpp
/// An unordered collection of tuples stored across a singly-linked chain of
/// slotted pages, all fetched through the buffer pool. This is the real,
/// disk-backed table storage the executor uses (no more in-memory heaps).

namespace minidb {

class TableHeap {
 public:
  TableHeap(BufferPoolManager* bpm, page_id_t first_page)
      : bpm_(bpm), first_page_(first_page) {}

  /// Allocates and initializes an empty first page for a new heap, returning
  /// its id (to be recorded in the catalog).
  static StatusOr<page_id_t> CreateFirstPage(BufferPoolManager* bpm);

  /// Inserts a serialized tuple, returning its record id.
  StatusOr<RID> InsertTuple(const std::string& tuple);

  /// Copies the tuple at @p rid into @p out. Returns false if absent.
  bool GetTuple(RID rid, std::string* out) const;

  /// Materializes every (rid, bytes) pair in the heap (used by sequential
  /// scan).
  std::vector<std::pair<RID, std::string>> Scan() const;

  /// Counts tuples by summing page slot counts (reads page headers only).
  std::size_t CountTuples() const;

  /// Empties every page in the chain but keeps the chain itself (for rewriting
  /// the catalog heap in place).
  Status Clear();

  page_id_t first_page() const { return first_page_; }

 private:
  BufferPoolManager* bpm_;
  page_id_t first_page_;
};

}  // namespace minidb
