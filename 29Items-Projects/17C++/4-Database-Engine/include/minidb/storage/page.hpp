#pragma once

#include <array>
#include <cstring>

#include "minidb/common/config.hpp"

/// @file page.hpp
/// A fixed-size in-memory frame holding one disk page plus the bookkeeping the
/// buffer pool needs to manage it (pin count, dirty flag, owning page id).

namespace minidb {

/// One 4 KiB frame in the buffer pool. The buffer pool is a `friend` and owns
/// the metadata; callers borrow `data()` while the page is pinned.
class Page {
 public:
  Page() { Reset(); }

  char* data() { return data_.data(); }
  const char* data() const { return data_.data(); }

  page_id_t page_id() const { return page_id_; }
  int pin_count() const { return pin_count_; }
  bool is_dirty() const { return is_dirty_; }

  static constexpr std::size_t kSize = PAGE_SIZE;

 private:
  friend class BufferPoolManager;

  /// Returns the frame to its empty, unpinned, clean state.
  void Reset() {
    data_.fill(0);
    page_id_ = INVALID_PAGE_ID;
    pin_count_ = 0;
    is_dirty_ = false;
  }

  std::array<char, PAGE_SIZE> data_{};
  page_id_t page_id_ = INVALID_PAGE_ID;
  int pin_count_ = 0;
  bool is_dirty_ = false;
};

}  // namespace minidb
