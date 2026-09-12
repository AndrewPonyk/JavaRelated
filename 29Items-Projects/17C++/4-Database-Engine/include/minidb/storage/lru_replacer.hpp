#pragma once

#include <cstddef>
#include <list>
#include <mutex>
#include <unordered_map>

#include "minidb/common/config.hpp"

/// @file lru_replacer.hpp
/// Least-Recently-Used victim selection for the buffer pool.
///
/// The replacer only ever tracks *evictable* (unpinned) frames. The buffer pool
/// `Unpin`s a frame when its pin count drops to zero (making it a candidate),
/// and `Pin`s it when it is fetched again (removing it from consideration).
/// `Victim` evicts the frame that has been unpinned the longest.
///
/// This class is fully implemented and exercised by
/// tests/test_lru_replacer.cpp.

namespace minidb {

class LRUReplacer {
 public:
  /// @param num_frames Upper bound on frames this replacer may track. Used for
  ///                   validation only; the replacer never grows beyond it.
  explicit LRUReplacer(std::size_t num_frames);

  ~LRUReplacer() = default;
  LRUReplacer(const LRUReplacer&) = delete;
  LRUReplacer& operator=(const LRUReplacer&) = delete;

  /// Evicts the least-recently-unpinned frame.
  /// @param[out] frame Receives the victim's frame id on success.
  /// @return true if a victim was found; false if no evictable frame exists.
  bool Victim(frame_id_t* frame);

  /// Marks a frame as in-use, removing it from the eviction set. No-op if the
  /// frame is not currently tracked.
  void Pin(frame_id_t frame);

  /// Marks a frame as evictable, adding it to the eviction set as the
  /// most-recently-used entry. No-op if already tracked or if at capacity.
  void Unpin(frame_id_t frame);

  /// Number of frames currently eligible for eviction.
  std::size_t Size() const;

 private:
  mutable std::mutex latch_;
  std::size_t capacity_;

  // Front = least recently unpinned (next victim); back = most recent.
  std::list<frame_id_t> lru_;
  std::unordered_map<frame_id_t, std::list<frame_id_t>::iterator> index_;
};

}  // namespace minidb
