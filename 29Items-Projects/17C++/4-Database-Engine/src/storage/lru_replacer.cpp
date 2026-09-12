#include "minidb/storage/lru_replacer.hpp"

namespace minidb {

LRUReplacer::LRUReplacer(std::size_t num_frames) : capacity_(num_frames) {}

bool LRUReplacer::Victim(frame_id_t* frame) {
  std::lock_guard<std::mutex> guard(latch_);
  if (lru_.empty()) {
    return false;
  }
  // The least-recently-unpinned frame lives at the front of the list.
  const frame_id_t victim = lru_.front();
  lru_.pop_front();
  index_.erase(victim);
  if (frame != nullptr) {
    *frame = victim;
  }
  return true;
}

void LRUReplacer::Pin(frame_id_t frame) {
  std::lock_guard<std::mutex> guard(latch_);
  auto it = index_.find(frame);
  if (it == index_.end()) {
    return;  // not currently evictable; nothing to do
  }
  lru_.erase(it->second);
  index_.erase(it);
}

void LRUReplacer::Unpin(frame_id_t frame) {
  std::lock_guard<std::mutex> guard(latch_);
  if (index_.count(frame) != 0) {
    return;  // already evictable; keep its original position
  }
  if (lru_.size() >= capacity_) {
    return;  // refuse to exceed the configured frame count
  }
  lru_.push_back(frame);
  index_[frame] = std::prev(lru_.end());
}

std::size_t LRUReplacer::Size() const {
  std::lock_guard<std::mutex> guard(latch_);
  return lru_.size();
}

}  // namespace minidb
