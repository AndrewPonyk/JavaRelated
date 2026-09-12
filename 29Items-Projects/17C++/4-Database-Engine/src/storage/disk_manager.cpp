#include "minidb/storage/disk_manager.hpp"

#include <cstring>

#include "minidb/common/config.hpp"

namespace minidb {

Status DiskManager::Open(const std::string& path) {
  // Map at least one page so the header page (page 0) always exists.
  MINIDB_RETURN_IF_ERROR(file_.Open(path, PAGE_SIZE));
  num_pages_ = file_.size() / PAGE_SIZE;
  if (num_pages_ == 0) num_pages_ = 1;  // header page

  // Page 0 is the superblock. Its magic/version are stamped and validated one
  // layer up, in Database::Open (see storage/header_page.hpp); the disk manager
  // itself stays format-agnostic.
  return Status::Ok();
}

Status DiskManager::EnsureMapped(page_id_t page_id) {
  const std::size_t need = (static_cast<std::size_t>(page_id) + 1) * PAGE_SIZE;
  return file_.EnsureSize(need);
}

StatusOr<page_id_t> DiskManager::AllocatePage() {
  const auto id = static_cast<page_id_t>(num_pages_);
  MINIDB_RETURN_IF_ERROR(EnsureMapped(id));
  ++num_pages_;
  return id;
}

Status DiskManager::ReadPage(page_id_t page_id, char* page_data) {
  if (page_id < 0 || page_data == nullptr) {
    return Status::InvalidArgument("ReadPage: bad page id or null buffer");
  }
  MINIDB_RETURN_IF_ERROR(EnsureMapped(page_id));
  const std::size_t offset = static_cast<std::size_t>(page_id) * PAGE_SIZE;
  std::memcpy(page_data, file_.data() + offset, PAGE_SIZE);
  return Status::Ok();
}

Status DiskManager::WritePage(page_id_t page_id, const char* page_data) {
  if (page_id < 0 || page_data == nullptr) {
    return Status::InvalidArgument("WritePage: bad page id or null buffer");
  }
  MINIDB_RETURN_IF_ERROR(EnsureMapped(page_id));
  const std::size_t offset = static_cast<std::size_t>(page_id) * PAGE_SIZE;
  std::memcpy(file_.data() + offset, page_data, PAGE_SIZE);
  if (static_cast<std::size_t>(page_id) >= num_pages_) {
    num_pages_ = static_cast<std::size_t>(page_id) + 1;
  }
  return Status::Ok();
}

Status DiskManager::Sync() {
  return file_.Sync();
}

}  // namespace minidb
