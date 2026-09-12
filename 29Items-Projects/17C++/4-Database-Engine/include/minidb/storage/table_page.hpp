#pragma once

#include <cstddef>
#include <cstdint>
#include <cstring>

#include "minidb/common/byte_io.hpp"
#include "minidb/common/config.hpp"

/// @file table_page.hpp
/// A classic **slotted page**: a thin accessor over a 4 KiB page buffer that
/// stores variable-length tuples. A slot directory grows from the front; tuple
/// payloads grow from the back. Pages are chained via `next_page_id` to form a
/// heap (see table_heap.hpp).
///
/// Layout:
///   [0..4)   next_page_id (i32)
///   [4..6)   slot_count   (u16)
///   [6..8)   free_offset  (u16)  -- payloads occupy [free_offset, PAGE_SIZE)
///   [8..)    slot array   -- per slot: offset (u16), length (u16)
///   payloads grow downward from PAGE_SIZE

namespace minidb {

class TablePage {
 public:
  explicit TablePage(char* data) : data_(data) {}

  static constexpr std::size_t kHeaderSize = 8;
  static constexpr std::size_t kSlotSize = 4;

  /// Initializes a brand-new, empty page (no chain successor).
  void Init() {
    set_next_page_id(INVALID_PAGE_ID);
    set_slot_count(0);
    set_free_offset(static_cast<std::uint16_t>(PAGE_SIZE));
  }

  /// Empties the page for reuse but preserves its chain successor.
  void Clear() {
    set_slot_count(0);
    set_free_offset(static_cast<std::uint16_t>(PAGE_SIZE));
  }

  page_id_t next_page_id() const { return LoadI32(data_ + 0); }
  void set_next_page_id(page_id_t id) { StoreI32(data_ + 0, id); }

  std::uint16_t slot_count() const { return LoadU16(data_ + 4); }
  std::uint16_t free_offset() const { return LoadU16(data_ + 6); }

  std::size_t free_space() const {
    const std::size_t slot_end = kHeaderSize + slot_count() * kSlotSize;
    return free_offset() >= slot_end ? free_offset() - slot_end : 0;
  }

  /// Appends a tuple. Returns false (no mutation) if it does not fit. On
  /// success writes the assigned slot index to @p out_slot.
  bool InsertTuple(const char* tuple, std::uint16_t len, slot_id_t* out_slot) {
    if (len + kSlotSize > free_space()) return false;
    const std::uint16_t offset =
        static_cast<std::uint16_t>(free_offset() - len);
    std::memcpy(data_ + offset, tuple, len);

    const std::uint16_t slot = slot_count();
    WriteSlot(slot, offset, len);
    set_slot_count(static_cast<std::uint16_t>(slot + 1));
    set_free_offset(offset);
    *out_slot = static_cast<slot_id_t>(slot);
    return true;
  }

  /// Borrows a tuple's bytes in place. Returns false for an out-of-range or
  /// tombstoned (length 0) slot.
  bool GetTuple(slot_id_t slot, const char** out, std::uint16_t* len) const {
    if (slot >= slot_count()) return false;
    std::uint16_t offset = 0;
    std::uint16_t length = 0;
    ReadSlot(slot, &offset, &length);
    if (length == 0) return false;
    *out = data_ + offset;
    *len = length;
    return true;
  }

 private:
  void set_slot_count(std::uint16_t n) { StoreU16(data_ + 4, n); }
  void set_free_offset(std::uint16_t off) { StoreU16(data_ + 6, off); }

  void WriteSlot(std::uint16_t slot, std::uint16_t offset, std::uint16_t len) {
    char* p = data_ + kHeaderSize + slot * kSlotSize;
    StoreU16(p + 0, offset);
    StoreU16(p + 2, len);
  }
  void ReadSlot(std::uint16_t slot, std::uint16_t* offset,
                std::uint16_t* len) const {
    const char* p = data_ + kHeaderSize + slot * kSlotSize;
    *offset = LoadU16(p + 0);
    *len = LoadU16(p + 2);
  }

  char* data_;
};

}  // namespace minidb
