#pragma once

#include <cstdint>
#include <cstring>

#include "minidb/common/config.hpp"

/// @file header_page.hpp
/// The superblock stored at page 0 of every database file. It records the magic
/// number / format version (so a foreign or stale file is rejected) and the
/// root of the system catalog heap, which is how schemas are rediscovered on
/// reopen. Read/written with a single memcpy of a POD struct — the file is only
/// ever read back by the same build.

namespace minidb {

struct Superblock {
  std::uint32_t magic = MINIDB_MAGIC;
  std::uint16_t format_version = FORMAT_VERSION;
  std::uint16_t reserved = 0;
  page_id_t catalog_root_page =
      INVALID_PAGE_ID;  ///< first page of catalog heap
};

static_assert(sizeof(Superblock) <= PAGE_SIZE,
              "superblock must fit in a single page");

inline void ReadSuperblock(const char* page, Superblock* out) {
  std::memcpy(out, page, sizeof(Superblock));
}

inline void WriteSuperblock(char* page, const Superblock& sb) {
  std::memcpy(page, &sb, sizeof(Superblock));
}

inline bool SuperblockValid(const Superblock& sb) {
  return sb.magic == MINIDB_MAGIC && sb.format_version == FORMAT_VERSION;
}

}  // namespace minidb
