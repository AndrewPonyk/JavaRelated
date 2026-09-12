#pragma once

#include <memory>
#include <string>

#include "minidb/catalog/catalog.hpp"
#include "minidb/common/status.hpp"
#include "minidb/execution/executor.hpp"
#include "minidb/execution/result_set.hpp"
#include "minidb/storage/buffer_pool_manager.hpp"
#include "minidb/storage/disk_manager.hpp"

/// @file database.hpp
/// The public façade. `Open` assembles the storage stack (mmap disk manager +
/// LRU buffer pool), catalog, and executor; `Execute` runs one SQL statement
/// through lex → parse → plan → execute. This is the only header an embedding
/// application needs.

namespace minidb {

class Database {
 public:
  // Move-only: owns the storage stack. All members are heap-allocated so moving
  // a Database never invalidates the internal pointers wiring them together.
  Database(Database&&) = default;
  Database& operator=(Database&&) = default;
  Database(const Database&) = delete;
  Database& operator=(const Database&) = delete;
  ~Database() = default;

  /// Opens (creating if absent) the database file at @p path.
  static StatusOr<Database> Open(const std::string& path);

  /// Parses and executes a single SQL statement.
  StatusOr<ResultSet> Execute(const std::string& sql);

  /// Flushes all dirty pages to disk (durability barrier).
  Status Flush();

  Catalog& catalog() { return *catalog_; }

 private:
  Database() = default;

  std::unique_ptr<DiskManager> disk_;
  std::unique_ptr<BufferPoolManager> buffer_pool_;
  std::unique_ptr<Catalog> catalog_;
  std::unique_ptr<Executor> executor_;
};

}  // namespace minidb
