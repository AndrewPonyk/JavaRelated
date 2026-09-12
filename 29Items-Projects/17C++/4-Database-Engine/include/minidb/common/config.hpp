#pragma once

#include <cstddef>
#include <cstdint>

/// @file config.hpp
/// Compile-time constants and primitive type aliases shared by every layer.
/// This is the bottom of the dependency graph: it includes nothing
/// project-specific and may be included by anything.

namespace minidb {

/// On-disk page size in bytes. Fixed for the lifetime of a database file.
/// 4 KiB matches the typical OS page size, which keeps mmap'd reads aligned to
/// a single minor fault per page.
inline constexpr std::size_t PAGE_SIZE = 4096;

/// Default number of frames in the buffer pool (overridable via the
/// MINIDB_BUFFER_POOL_PAGES environment variable). 1024 * 4 KiB = 4 MiB.
inline constexpr std::size_t DEFAULT_BUFFER_POOL_PAGES = 1024;

/// Maximum length, in bytes, of a SQL identifier or string literal. Acts as a
/// guard against resource-exhaustion from pathological input.
inline constexpr std::size_t MAX_IDENTIFIER_LEN = 128;

// --- Primitive id types ------------------------------------------------------

using page_id_t = std::int32_t;   ///< Index of a page within the heap file.
using frame_id_t = std::int32_t;  ///< Index of a frame within the buffer pool.
using slot_id_t = std::uint16_t;  ///< Slot within a page's slot directory.
using oid_t = std::uint32_t;      ///< Catalog object id (tables, indexes).
using lsn_t = std::int64_t;       ///< Log sequence number (reserved for WAL).

inline constexpr page_id_t INVALID_PAGE_ID = -1;
inline constexpr oid_t INVALID_OID = 0;

// --- File header (page 0) ----------------------------------------------------

/// Magic number written to the file header page: ASCII "MNID".
inline constexpr std::uint32_t MINIDB_MAGIC = 0x4D4E4944;

/// On-disk format version. Bumping this is a major-version event and requires a
/// migration path (see docs/TECH-NOTES.md, "Deployment Strategy").
inline constexpr std::uint16_t FORMAT_VERSION = 1;

}  // namespace minidb
