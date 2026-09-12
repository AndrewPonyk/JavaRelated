#pragma once

#include <cstdint>
#include <optional>
#include <vector>

#include "minidb/common/status.hpp"
#include "minidb/common/types.hpp"
#include "minidb/index/bplus_tree_page.hpp"
#include "minidb/storage/buffer_pool_manager.hpp"

/// @file bplus_tree.hpp
/// A disk-resident B+ Tree mapping a 64-bit key to a record id (RID). Nodes are
/// pages fetched through the buffer pool; leaves are linked for ordered range
/// scans. Insert splits overflowing nodes and grows the root; delete removes
/// from the leaf (space is reclaimed lazily — see note below).
///
/// The root page id is exposed via `root_page_id()` so the catalog can persist
/// it: it changes on the first insert and whenever the root splits.
///
/// Concurrency: single-writer (the engine holds a coarse latch). Merging on
/// delete is intentionally deferred — it is a space optimization, not a
/// correctness requirement, and is tracked as future work in PROJECT-PLAN.md.

namespace minidb {

class BPlusTree {
 public:
  using KeyType = std::int64_t;

  BPlusTree(BufferPoolManager* bpm, page_id_t root = INVALID_PAGE_ID,
            std::uint16_t leaf_max = MaxLeafEntries(),
            std::uint16_t internal_max = MaxInternalChildren());

  /// Inserts (key -> rid). Returns kAlreadyExists if the key is present.
  Status Insert(KeyType key, const RID& rid);

  /// Removes @p key. Returns kNotFound if absent.
  Status Erase(KeyType key);

  /// Point lookup. nullopt if absent.
  std::optional<RID> GetValue(KeyType key) const;

  /// Inclusive range scan [low, high] in ascending key order.
  std::vector<RID> RangeScan(KeyType low, KeyType high) const;

  bool empty() const { return root_page_id_ == INVALID_PAGE_ID || size() == 0; }
  std::size_t size() const;
  page_id_t root_page_id() const { return root_page_id_; }

 private:
  // Result of a child insert that bubbled a split up to its parent.
  struct Split {
    bool happened = false;
    KeyType key = 0;
    page_id_t new_page = INVALID_PAGE_ID;
  };

  Split InsertInto(page_id_t page_id, KeyType key, const RID& rid,
                   bool* duplicate);
  page_id_t FindLeaf(KeyType key) const;  // descend to the leaf for key
  page_id_t LeftmostLeaf() const;         // descend taking child 0

  BufferPoolManager* bpm_;
  page_id_t root_page_id_;
  std::uint16_t leaf_max_;
  std::uint16_t internal_max_;
};

}  // namespace minidb
