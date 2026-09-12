#include "minidb/storage/buffer_pool_manager.hpp"

namespace minidb {

BufferPoolManager::BufferPoolManager(std::size_t pool_size,
                                     DiskManager* disk_manager)
    : pool_size_(pool_size),
      disk_manager_(disk_manager),
      frames_(pool_size),
      replacer_(pool_size) {
  for (std::size_t i = 0; i < pool_size_; ++i) {
    free_list_.push_back(static_cast<frame_id_t>(i));
  }
}

bool BufferPoolManager::GetVictimFrame(frame_id_t* out_frame) {
  // Prefer an unused frame.
  if (!free_list_.empty()) {
    *out_frame = free_list_.front();
    free_list_.pop_front();
    return true;
  }
  // Otherwise evict the LRU victim, writing it back if dirty.
  frame_id_t victim = 0;
  if (!replacer_.Victim(&victim)) {
    return false;  // every frame is pinned
  }
  Page& page = frames_[victim];
  if (page.is_dirty_) {
    // NOTE: a write-back failure here is best-effort — the frame is still
    // reclaimed. Propagating this error (and the durability guarantees that go
    // with it) is part of the WAL work in docs/PROJECT-PLAN.md, Phase 3.
    if (auto s = disk_manager_->WritePage(page.page_id_, page.data());
        !s.ok()) {
      (void)s;
    }
    page.is_dirty_ = false;
  }
  page_table_.erase(page.page_id_);
  *out_frame = victim;
  return true;
}

Page* BufferPoolManager::FetchPage(page_id_t page_id) {
  std::lock_guard<std::mutex> guard(latch_);

  if (auto it = page_table_.find(page_id); it != page_table_.end()) {
    const frame_id_t fid = it->second;
    Page& page = frames_[fid];
    ++page.pin_count_;
    replacer_.Pin(fid);  // in use again -> not evictable
    return &page;
  }

  frame_id_t fid = 0;
  if (!GetVictimFrame(&fid)) {
    return nullptr;
  }

  Page& page = frames_[fid];
  page.Reset();
  if (auto s = disk_manager_->ReadPage(page_id, page.data()); !s.ok()) {
    free_list_.push_back(fid);  // hand the frame back
    return nullptr;
  }
  page.page_id_ = page_id;
  page.pin_count_ = 1;
  page.is_dirty_ = false;
  page_table_[page_id] = fid;
  replacer_.Pin(fid);
  return &page;
}

Page* BufferPoolManager::NewPage(page_id_t* page_id) {
  std::lock_guard<std::mutex> guard(latch_);

  frame_id_t fid = 0;
  if (!GetVictimFrame(&fid)) {
    return nullptr;
  }

  auto alloc = disk_manager_->AllocatePage();
  if (!alloc.ok()) {
    free_list_.push_back(fid);
    return nullptr;
  }
  const page_id_t new_id = alloc.value();

  Page& page = frames_[fid];
  page.Reset();
  page.page_id_ = new_id;
  page.pin_count_ = 1;
  page.is_dirty_ = true;  // a freshly allocated page must be written back
  page_table_[new_id] = fid;
  replacer_.Pin(fid);
  if (page_id != nullptr) {
    *page_id = new_id;
  }
  return &page;
}

bool BufferPoolManager::UnpinPage(page_id_t page_id, bool is_dirty) {
  std::lock_guard<std::mutex> guard(latch_);
  auto it = page_table_.find(page_id);
  if (it == page_table_.end()) {
    return false;
  }
  Page& page = frames_[it->second];
  if (page.pin_count_ <= 0) {
    return false;
  }
  --page.pin_count_;
  if (is_dirty) {
    page.is_dirty_ = true;
  }
  if (page.pin_count_ == 0) {
    replacer_.Unpin(it->second);  // now a candidate for eviction
  }
  return true;
}

bool BufferPoolManager::FlushPage(page_id_t page_id) {
  std::lock_guard<std::mutex> guard(latch_);
  auto it = page_table_.find(page_id);
  if (it == page_table_.end()) {
    return false;
  }
  Page& page = frames_[it->second];
  if (!disk_manager_->WritePage(page_id, page.data()).ok()) {
    return false;
  }
  page.is_dirty_ = false;
  return true;
}

Status BufferPoolManager::FlushAll() {
  std::lock_guard<std::mutex> guard(latch_);
  for (auto& [pid, fid] : page_table_) {
    Page& page = frames_[fid];
    if (page.is_dirty_) {
      MINIDB_RETURN_IF_ERROR(disk_manager_->WritePage(pid, page.data()));
      page.is_dirty_ = false;
    }
  }
  return disk_manager_->Sync();
}

}  // namespace minidb
