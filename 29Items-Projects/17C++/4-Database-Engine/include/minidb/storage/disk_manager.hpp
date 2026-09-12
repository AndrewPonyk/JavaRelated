#pragma once

#include <cstddef>
#include <string>

#include "minidb/common/config.hpp"
#include "minidb/common/status.hpp"
#include "minidb/storage/mmap_file.hpp"

/// @file disk_manager.hpp
/// Translates page ids to byte offsets within a single memory-mapped heap file.
/// This is the only component that performs file I/O; the buffer pool sits on
/// top of it. Page 0 is reserved for the file header.

namespace minidb {

class DiskManager {
 public:
  DiskManager() = default;
  ~DiskManager() = default;

  DiskManager(const DiskManager&) = delete;
  DiskManager& operator=(const DiskManager&) = delete;

  /// Opens (creating if absent) the database file at @p path and maps it.
  Status Open(const std::string& path);

  /// Reserves a new page at the end of the file and returns its id.
  StatusOr<page_id_t> AllocatePage();

  /// Copies PAGE_SIZE bytes for @p page_id into @p page_data.
  Status ReadPage(page_id_t page_id, char* page_data);

  /// Copies PAGE_SIZE bytes from @p page_data into the mapping for @p page_id.
  /// Visible to other readers immediately; durable only after Sync().
  Status WritePage(page_id_t page_id, const char* page_data);

  /// Flushes all dirty mapped pages to stable storage.
  Status Sync();

  /// Total number of allocated pages (including the header page).
  std::size_t num_pages() const { return num_pages_; }

 private:
  /// Ensures the mapping covers at least (page_id + 1) pages.
  Status EnsureMapped(page_id_t page_id);

  MmapFile file_;
  std::size_t num_pages_ = 0;
};

}  // namespace minidb
