#pragma once

#include <cstddef>
#include <cstdint>

#include "minidb/common/byte_io.hpp"
#include "minidb/common/config.hpp"
#include "minidb/common/types.hpp"

/// @file bplus_tree_page.hpp
/// Accessors for the two B+ Tree node kinds, laid over a raw 4 KiB page buffer.
/// Offset-based (memcpy) access keeps the on-page format explicit and avoids
/// the UB of overlaying a struct with a flexible array member on a `char*`.
///
/// Shared header (both kinds):
///   [0]   page_type (u8): 1 = leaf, 2 = internal
///   [2]   size      (u16): leaf -> entry count; internal -> child count
///   [4]   max_size  (u16): capacity (entries for leaf, children for internal)
///   [8]   next_page (i32): leaf-only sibling link for range scans
/// Data area begins at offset 16.
///   Leaf entry  (16 bytes): key (i64) | page_id (i32) | slot (u16) | pad (u16)
///   Internal pair (12 bytes): key (i64) | child_page_id (i32); key[0] unused

namespace minidb {

inline constexpr std::uint8_t kLeafPage = 1;
inline constexpr std::uint8_t kInternalPage = 2;

namespace bpt_layout {
inline constexpr std::size_t kOffType = 0;
inline constexpr std::size_t kOffSize = 2;
inline constexpr std::size_t kOffMaxSize = 4;
inline constexpr std::size_t kOffNext = 8;
inline constexpr std::size_t kData = 16;
inline constexpr std::size_t kLeafEntry = 16;
inline constexpr std::size_t kInternalEntry = 12;
}  // namespace bpt_layout

// Production default capacities. We reserve ONE slot below the physical page
// capacity because insert writes the overflowing entry into the node *before*
// splitting; that transient (max_size + 1)-th entry must still land in-page.
inline constexpr std::uint16_t MaxLeafEntries() {
  return static_cast<std::uint16_t>(
      (PAGE_SIZE - bpt_layout::kData) / bpt_layout::kLeafEntry - 1);
}
inline constexpr std::uint16_t MaxInternalChildren() {
  return static_cast<std::uint16_t>(
      (PAGE_SIZE - bpt_layout::kData) / bpt_layout::kInternalEntry - 1);
}

/// Common header view shared by both node kinds.
class BPlusTreePage {
 public:
  explicit BPlusTreePage(char* data) : data_(data) {}

  std::uint8_t page_type() const {
    return static_cast<std::uint8_t>(data_[bpt_layout::kOffType]);
  }
  bool is_leaf() const { return page_type() == kLeafPage; }

  std::uint16_t size() const { return LoadU16(data_ + bpt_layout::kOffSize); }
  void set_size(std::uint16_t n) { StoreU16(data_ + bpt_layout::kOffSize, n); }

  std::uint16_t max_size() const {
    return LoadU16(data_ + bpt_layout::kOffMaxSize);
  }

 protected:
  void set_type(std::uint8_t t) {
    data_[bpt_layout::kOffType] = static_cast<char>(t);
  }
  void set_max_size(std::uint16_t n) {
    StoreU16(data_ + bpt_layout::kOffMaxSize, n);
  }
  char* data_;
};

/// Leaf node: sorted (key -> RID) entries plus a sibling pointer.
class LeafPage : public BPlusTreePage {
 public:
  explicit LeafPage(char* data) : BPlusTreePage(data) {}

  void Init(std::uint16_t max) {
    set_type(kLeafPage);
    set_size(0);
    set_max_size(max);
    set_next_page_id(INVALID_PAGE_ID);
  }

  page_id_t next_page_id() const {
    return LoadI32(data_ + bpt_layout::kOffNext);
  }
  void set_next_page_id(page_id_t id) {
    StoreI32(data_ + bpt_layout::kOffNext, id);
  }

  std::int64_t KeyAt(std::uint16_t i) const { return LoadI64(EntryPtr(i)); }
  RID RidAt(std::uint16_t i) const {
    const char* p = EntryPtr(i);
    return RID{LoadI32(p + 8), LoadU16(p + 12)};
  }

  /// First index whose key is >= @p key (binary search).
  std::uint16_t LowerBound(std::int64_t key) const {
    std::uint16_t lo = 0;
    std::uint16_t hi = size();
    while (lo < hi) {
      const std::uint16_t mid = static_cast<std::uint16_t>(lo + (hi - lo) / 2);
      if (KeyAt(mid) < key) {
        lo = static_cast<std::uint16_t>(mid + 1);
      } else {
        hi = mid;
      }
    }
    return lo;
  }

  /// Inserts keeping sorted order. Returns false if the key already exists.
  bool Insert(std::int64_t key, RID rid) {
    const std::uint16_t pos = LowerBound(key);
    if (pos < size() && KeyAt(pos) == key) return false;
    for (std::uint16_t i = size(); i > pos; --i) {
      SetEntry(i, KeyAt(static_cast<std::uint16_t>(i - 1)),
               RidAt(static_cast<std::uint16_t>(i - 1)));
    }
    SetEntry(pos, key, rid);
    set_size(static_cast<std::uint16_t>(size() + 1));
    return true;
  }

  bool Remove(std::int64_t key) {
    const std::uint16_t pos = LowerBound(key);
    if (pos >= size() || KeyAt(pos) != key) return false;
    for (std::uint16_t i = pos; i + 1 < size(); ++i) {
      SetEntry(i, KeyAt(static_cast<std::uint16_t>(i + 1)),
               RidAt(static_cast<std::uint16_t>(i + 1)));
    }
    set_size(static_cast<std::uint16_t>(size() - 1));
    return true;
  }

  void SetEntry(std::uint16_t i, std::int64_t key, RID rid) {
    char* p = EntryPtr(i);
    StoreI64(p + 0, key);
    StoreI32(p + 8, rid.page_id);
    StoreU16(p + 12, rid.slot);
    StoreU16(p + 14, 0);  // padding
  }

 private:
  char* EntryPtr(std::uint16_t i) {
    return data_ + bpt_layout::kData + i * bpt_layout::kLeafEntry;
  }
  const char* EntryPtr(std::uint16_t i) const {
    return data_ + bpt_layout::kData + i * bpt_layout::kLeafEntry;
  }
};

/// Internal node: `size` children and `size - 1` separator keys (key[0]
/// unused).
class InternalPage : public BPlusTreePage {
 public:
  explicit InternalPage(char* data) : BPlusTreePage(data) {}

  void Init(std::uint16_t max) {
    set_type(kInternalPage);
    set_size(0);
    set_max_size(max);
  }

  std::int64_t KeyAt(std::uint16_t i) const { return LoadI64(PairPtr(i) + 0); }
  page_id_t ChildAt(std::uint16_t i) const { return LoadI32(PairPtr(i) + 8); }
  void SetKeyAt(std::uint16_t i, std::int64_t k) {
    StoreI64(PairPtr(i) + 0, k);
  }
  void SetChildAt(std::uint16_t i, page_id_t c) { StoreI32(PairPtr(i) + 8, c); }

  /// Index of the child to follow when searching for @p key.
  std::uint16_t ChildIndexFor(std::int64_t key) const {
    // Keys live at [1, size). Find the last key <= key; descend its child.
    std::uint16_t lo = 1;
    std::uint16_t hi = size();
    while (lo < hi) {
      const std::uint16_t mid = static_cast<std::uint16_t>(lo + (hi - lo) / 2);
      if (KeyAt(mid) <= key) {
        lo = static_cast<std::uint16_t>(mid + 1);
      } else {
        hi = mid;
      }
    }
    return static_cast<std::uint16_t>(lo - 1);
  }

  /// Inserts (key, child) immediately after the pair at @p after_index.
  void InsertAfter(std::uint16_t after_index, std::int64_t key,
                   page_id_t child) {
    const std::uint16_t pos = static_cast<std::uint16_t>(after_index + 1);
    for (std::uint16_t i = size(); i > pos; --i) {
      SetKeyAt(i, KeyAt(static_cast<std::uint16_t>(i - 1)));
      SetChildAt(i, ChildAt(static_cast<std::uint16_t>(i - 1)));
    }
    SetKeyAt(pos, key);
    SetChildAt(pos, child);
    set_size(static_cast<std::uint16_t>(size() + 1));
  }

  /// Initializes a fresh root with two children separated by @p key.
  void PopulateNewRoot(page_id_t left, std::int64_t key, page_id_t right) {
    SetChildAt(0, left);
    SetKeyAt(1, key);
    SetChildAt(1, right);
    set_size(2);
  }

 private:
  char* PairPtr(std::uint16_t i) {
    return data_ + bpt_layout::kData + i * bpt_layout::kInternalEntry;
  }
  const char* PairPtr(std::uint16_t i) const {
    return data_ + bpt_layout::kData + i * bpt_layout::kInternalEntry;
  }
};

}  // namespace minidb
