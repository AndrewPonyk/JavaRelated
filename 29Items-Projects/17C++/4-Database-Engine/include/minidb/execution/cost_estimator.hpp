#pragma once

#include <cstddef>

#include "minidb/parser/ast.hpp"

/// @file cost_estimator.hpp
/// A deliberately simple, heuristic cost model used by the planner to choose
/// between a sequential scan and an index scan. No histograms or sampling yet
/// (that's Phase 3) — just page-count / selectivity arithmetic. Fully
/// implemented and unit-tested (tests/test_cost_estimator.cpp).

namespace minidb {

/// Coarse table statistics the planner feeds the cost model.
struct TableStats {
  std::size_t row_count = 0;
  std::size_t page_count = 0;
};

/// A cost broken into its I/O and CPU components (abstract "cost units").
struct ScanCost {
  double io_cost = 0.0;
  double cpu_cost = 0.0;
  double total() const { return io_cost + cpu_cost; }
};

class CostEstimator {
 public:
  // Tunable cost constants (abstract units; ratios are what matter).
  static constexpr double kSeqPageCost = 1.0;     ///< sequential page read
  static constexpr double kRandomPageCost = 4.0;  ///< random page read
  static constexpr double kCpuTupleCost = 0.01;   ///< predicate eval / tuple
  static constexpr double kCpuIndexTupleCost = 0.005;
  static constexpr std::size_t kIndexFanout = 100;  ///< keys per B+ Tree node

  /// Fraction of rows a predicate is expected to match (no stats: defaults).
  static double EstimateSelectivity(CompareOp op);

  /// Cost of reading every page and evaluating the predicate on every row.
  static ScanCost SeqScan(const TableStats& stats, double selectivity);

  /// Cost of descending the B+ Tree and fetching the matching rows.
  static ScanCost IndexScan(const TableStats& stats, double selectivity);

  /// True when an index scan is estimated cheaper than a sequential scan.
  static bool PreferIndex(const TableStats& stats, double selectivity);
};

}  // namespace minidb
