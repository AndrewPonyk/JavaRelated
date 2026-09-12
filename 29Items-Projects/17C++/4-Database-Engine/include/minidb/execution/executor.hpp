#pragma once

#include "minidb/catalog/catalog.hpp"
#include "minidb/common/status.hpp"
#include "minidb/execution/result_set.hpp"
#include "minidb/parser/ast.hpp"
#include "minidb/storage/buffer_pool_manager.hpp"

/// @file executor.hpp
/// Plans and runs a parsed `Statement` against disk-backed storage. The planner
/// uses the CostEstimator to choose between a sequential scan (over the table
/// heap) and an index scan (via the primary-key B+ Tree). The chosen plan and
/// its estimated cost are reported in the ResultSet message, and `EXPLAIN`
/// returns the plan instead of running it.
///
/// The first integer column of each table is treated as a unique primary key
/// and indexed. All tuple storage flows through the buffer pool.

namespace minidb {

class Executor {
 public:
  Executor(Catalog* catalog, BufferPoolManager* bpm)
      : catalog_(catalog), bpm_(bpm) {}

  StatusOr<ResultSet> Execute(const Statement& statement);

 private:
  StatusOr<ResultSet> ExecCreateTable(const CreateTableStatement& stmt);
  StatusOr<ResultSet> ExecInsert(const InsertStatement& stmt);
  StatusOr<ResultSet> ExecSelect(const SelectStatement& stmt);

  Catalog* catalog_;
  BufferPoolManager* bpm_;
};

}  // namespace minidb
