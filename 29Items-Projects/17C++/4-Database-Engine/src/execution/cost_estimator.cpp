#include "minidb/execution/cost_estimator.hpp"

#include <algorithm>
#include <cmath>

namespace minidb {

double CostEstimator::EstimateSelectivity(CompareOp op) {
  switch (op) {
    case CompareOp::kEq:
      return 0.01;  // equality is selective
    case CompareOp::kNe:
      return 0.90;  // inequality matches almost everything
    case CompareOp::kLt:
    case CompareOp::kLe:
    case CompareOp::kGt:
    case CompareOp::kGe:
      return 0.30;  // a range matches a moderate fraction
  }
  return 0.50;
}

ScanCost CostEstimator::SeqScan(const TableStats& stats, double selectivity) {
  // A sequential scan reads every page and tests every row regardless of how
  // selective the predicate is; selectivity only affects the output size.
  (void)selectivity;
  ScanCost cost;
  cost.io_cost = static_cast<double>(stats.page_count) * kSeqPageCost;
  cost.cpu_cost = static_cast<double>(stats.row_count) * kCpuTupleCost;
  return cost;
}

ScanCost CostEstimator::IndexScan(const TableStats& stats, double selectivity) {
  selectivity = std::clamp(selectivity, 0.0, 1.0);
  const double matched =
      std::max(1.0, static_cast<double>(stats.row_count) * selectivity);

  // Tree height ~ log_fanout(row_count).
  double height = 1.0;
  if (stats.row_count > 1) {
    height = std::ceil(std::log(static_cast<double>(stats.row_count)) /
                       std::log(static_cast<double>(kIndexFanout)));
    height = std::max(1.0, height);
  }

  ScanCost cost;
  cost.io_cost = height * kRandomPageCost  // walk internal nodes to a leaf
                 +
                 matched * kRandomPageCost;  // one random heap fetch per match
  cost.cpu_cost = matched * kCpuIndexTupleCost;
  return cost;
}

bool CostEstimator::PreferIndex(const TableStats& stats, double selectivity) {
  return IndexScan(stats, selectivity).total() <
         SeqScan(stats, selectivity).total();
}

}  // namespace minidb
