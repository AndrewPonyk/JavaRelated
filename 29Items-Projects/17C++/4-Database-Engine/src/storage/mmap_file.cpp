#include "minidb/storage/mmap_file.hpp"

#include <algorithm>
#include <utility>

#include "minidb/common/config.hpp"

#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#else
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#include <cerrno>
#include <cstring>
#endif

namespace minidb {
namespace {

std::size_t RoundUpToPage(std::size_t n) {
  if (n == 0) return PAGE_SIZE;
  return ((n + PAGE_SIZE - 1) / PAGE_SIZE) * PAGE_SIZE;
}

}  // namespace

MmapFile::~MmapFile() {
  Close();
}

MmapFile::MmapFile(MmapFile&& other) noexcept {
  MoveFrom(other);
}

MmapFile& MmapFile::operator=(MmapFile&& other) noexcept {
  if (this != &other) {
    Close();
    MoveFrom(other);
  }
  return *this;
}

void MmapFile::MoveFrom(MmapFile& other) noexcept {
  path_ = std::move(other.path_);
  base_ = other.base_;
  mapped_bytes_ = other.mapped_bytes_;
  other.base_ = nullptr;
  other.mapped_bytes_ = 0;
#ifdef _WIN32
  file_handle_ = other.file_handle_;
  mapping_handle_ = other.mapping_handle_;
  other.file_handle_ = nullptr;
  other.mapping_handle_ = nullptr;
#else
  fd_ = other.fd_;
  other.fd_ = -1;
#endif
}

// ----------------------------------------------------------------------------
#ifdef _WIN32  // Windows: CreateFileMapping / MapViewOfFile
// ----------------------------------------------------------------------------

Status MmapFile::Open(const std::string& path, std::size_t min_bytes) {
  Close();
  path_ = path;
  std::size_t want = RoundUpToPage(std::max<std::size_t>(min_bytes, PAGE_SIZE));

  HANDLE fh =
      ::CreateFileA(path.c_str(), GENERIC_READ | GENERIC_WRITE, FILE_SHARE_READ,
                    nullptr, OPEN_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
  if (fh == INVALID_HANDLE_VALUE) {
    return Status::IOError("CreateFile failed for " + path);
  }

  LARGE_INTEGER fsize;
  if (!::GetFileSizeEx(fh, &fsize)) {
    ::CloseHandle(fh);
    return Status::IOError("GetFileSizeEx failed");
  }
  const std::size_t current = static_cast<std::size_t>(fsize.QuadPart);
  if (current > want) want = RoundUpToPage(current);

  ULARGE_INTEGER cap;
  cap.QuadPart = want;
  HANDLE mh = ::CreateFileMappingA(fh, nullptr, PAGE_READWRITE, cap.HighPart,
                                   cap.LowPart, nullptr);
  if (mh == nullptr) {
    ::CloseHandle(fh);
    return Status::IOError("CreateFileMapping failed");
  }

  void* base = ::MapViewOfFile(mh, FILE_MAP_ALL_ACCESS, 0, 0, want);
  if (base == nullptr) {
    ::CloseHandle(mh);
    ::CloseHandle(fh);
    return Status::IOError("MapViewOfFile failed");
  }

  file_handle_ = fh;
  mapping_handle_ = mh;
  base_ = static_cast<char*>(base);
  mapped_bytes_ = want;
  return Status::Ok();
}

Status MmapFile::EnsureSize(std::size_t bytes) {
  if (!is_open()) return Status::IOError("EnsureSize on a closed file");
  if (bytes <= mapped_bytes_) return Status::Ok();
  const std::size_t want = RoundUpToPage(bytes);

  // Tear down the current view/mapping; the underlying file handle stays open.
  ::UnmapViewOfFile(base_);
  ::CloseHandle(static_cast<HANDLE>(mapping_handle_));
  base_ = nullptr;
  mapping_handle_ = nullptr;

  ULARGE_INTEGER cap;
  cap.QuadPart = want;
  HANDLE mh =
      ::CreateFileMappingA(static_cast<HANDLE>(file_handle_), nullptr,
                           PAGE_READWRITE, cap.HighPart, cap.LowPart, nullptr);
  if (mh == nullptr) {
    mapped_bytes_ = 0;
    return Status::IOError("CreateFileMapping (grow) failed");
  }
  void* base = ::MapViewOfFile(mh, FILE_MAP_ALL_ACCESS, 0, 0, want);
  if (base == nullptr) {
    ::CloseHandle(mh);
    mapped_bytes_ = 0;
    return Status::IOError("MapViewOfFile (grow) failed");
  }
  mapping_handle_ = mh;
  base_ = static_cast<char*>(base);
  mapped_bytes_ = want;
  return Status::Ok();
}

Status MmapFile::Sync() {
  if (!is_open()) return Status::Ok();
  if (::FlushViewOfFile(base_, mapped_bytes_) == 0) {
    return Status::IOError("FlushViewOfFile failed");
  }
  if (::FlushFileBuffers(static_cast<HANDLE>(file_handle_)) == 0) {
    return Status::IOError("FlushFileBuffers failed");
  }
  return Status::Ok();
}

void MmapFile::Close() {
  if (base_ != nullptr) ::UnmapViewOfFile(base_);
  if (mapping_handle_ != nullptr)
    ::CloseHandle(static_cast<HANDLE>(mapping_handle_));
  if (file_handle_ != nullptr) ::CloseHandle(static_cast<HANDLE>(file_handle_));
  base_ = nullptr;
  mapping_handle_ = nullptr;
  file_handle_ = nullptr;
  mapped_bytes_ = 0;
}

// ----------------------------------------------------------------------------
#else  // POSIX: mmap / munmap / msync
// ----------------------------------------------------------------------------

Status MmapFile::Open(const std::string& path, std::size_t min_bytes) {
  Close();
  path_ = path;
  std::size_t want = RoundUpToPage(std::max<std::size_t>(min_bytes, PAGE_SIZE));

  int fd = ::open(path.c_str(), O_RDWR | O_CREAT, 0644);
  if (fd < 0) {
    return Status::IOError("open failed: " + std::string(std::strerror(errno)));
  }

  struct stat st {};
  if (::fstat(fd, &st) != 0) {
    ::close(fd);
    return Status::IOError("fstat failed");
  }
  const std::size_t current = static_cast<std::size_t>(st.st_size);
  if (current > want) want = RoundUpToPage(current);

  if (current < want && ::ftruncate(fd, static_cast<off_t>(want)) != 0) {
    ::close(fd);
    return Status::IOError("ftruncate failed");
  }

  void* base = ::mmap(nullptr, want, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
  if (base == MAP_FAILED) {
    ::close(fd);
    return Status::IOError("mmap failed: " + std::string(std::strerror(errno)));
  }

  fd_ = fd;
  base_ = static_cast<char*>(base);
  mapped_bytes_ = want;
  return Status::Ok();
}

Status MmapFile::EnsureSize(std::size_t bytes) {
  if (!is_open()) return Status::IOError("EnsureSize on a closed file");
  if (bytes <= mapped_bytes_) return Status::Ok();
  const std::size_t want = RoundUpToPage(bytes);

  if (::ftruncate(fd_, static_cast<off_t>(want)) != 0) {
    return Status::IOError("ftruncate (grow) failed");
  }
  // Remap. Portable approach: unmap then map again (mapping may relocate).
  if (::munmap(base_, mapped_bytes_) != 0) {
    base_ = nullptr;
    mapped_bytes_ = 0;
    return Status::IOError("munmap (grow) failed");
  }
  void* base =
      ::mmap(nullptr, want, PROT_READ | PROT_WRITE, MAP_SHARED, fd_, 0);
  if (base == MAP_FAILED) {
    base_ = nullptr;
    mapped_bytes_ = 0;
    return Status::IOError("mmap (grow) failed");
  }
  base_ = static_cast<char*>(base);
  mapped_bytes_ = want;
  return Status::Ok();
}

Status MmapFile::Sync() {
  if (!is_open()) return Status::Ok();
  if (::msync(base_, mapped_bytes_, MS_SYNC) != 0) {
    return Status::IOError("msync failed: " +
                           std::string(std::strerror(errno)));
  }
  return Status::Ok();
}

void MmapFile::Close() {
  if (base_ != nullptr) ::munmap(base_, mapped_bytes_);
  if (fd_ >= 0) ::close(fd_);
  base_ = nullptr;
  mapped_bytes_ = 0;
  fd_ = -1;
}

#endif  // _WIN32

}  // namespace minidb
