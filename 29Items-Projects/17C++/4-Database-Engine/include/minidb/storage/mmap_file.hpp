#pragma once

#include <cstddef>
#include <string>

#include "minidb/common/status.hpp"

/// @file mmap_file.hpp
/// RAII wrapper over a memory-mapped file. Hides the POSIX (`mmap`/`munmap`/
/// `msync`) vs. Windows (`CreateFileMapping`/`MapViewOfFile`/`FlushViewOfFile`)
/// divergence behind one interface. See docs/TECH-NOTES.md §3.6 for the mmap
/// pitfalls this class exists to contain.
///
/// IMPORTANT: `EnsureSize` may move the mapping. Any raw pointer obtained from
/// `data()` is invalidated by a subsequent `EnsureSize` that grows the file.

namespace minidb {

class MmapFile {
 public:
  MmapFile() = default;
  ~MmapFile();

  MmapFile(const MmapFile&) = delete;
  MmapFile& operator=(const MmapFile&) = delete;
  MmapFile(MmapFile&& other) noexcept;
  MmapFile& operator=(MmapFile&& other) noexcept;

  /// Opens (creating if absent) @p path and maps at least @p min_bytes,
  /// rounded up to a multiple of PAGE_SIZE (minimum one page).
  Status Open(const std::string& path, std::size_t min_bytes);

  /// Grows the file and remaps so the mapping is at least @p bytes. Cheap no-op
  /// if the mapping already covers @p bytes. May relocate the mapping.
  Status EnsureSize(std::size_t bytes);

  /// Flushes dirty mapped pages to stable storage (durability barrier).
  Status Sync();

  /// Unmaps and closes. Safe to call multiple times.
  void Close();

  char* data() { return base_; }
  const char* data() const { return base_; }
  std::size_t size() const { return mapped_bytes_; }
  bool is_open() const { return base_ != nullptr; }

 private:
  void MoveFrom(MmapFile& other) noexcept;

  std::string path_;
  char* base_ = nullptr;
  std::size_t mapped_bytes_ = 0;

#ifdef _WIN32
  void* file_handle_ = nullptr;     // HANDLE
  void* mapping_handle_ = nullptr;  // HANDLE
#else
  int fd_ = -1;
#endif
};

}  // namespace minidb
