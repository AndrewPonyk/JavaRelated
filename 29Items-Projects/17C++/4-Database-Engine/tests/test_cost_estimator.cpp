// Unit tests for the heuristic cost model. Fully implemented module.

#include <gtest/gtest.h>

#include "minidb/execution/cost_estimator.hpp"

using minidb::CompareOp;
using minidb::CostEstimator;
using minidb::TableStats;

TEST(CostEstimator, SelectivityIsAFractionAndOrdered) {
  for (CompareOp op : {CompareOp::kEq, CompareOp::kNe, CompareOp::kLt,
                       CompareOp::kLe, CompareOp::kGt, CompareOp::kGe}) {
    const double s = CostEstimator::EstimateSelectivity(op);
    EXPECT_GT(s, 0.0);
    EXPECT_LE(s, 1.0);
  }
  // Equality is more selective than a range, which beats inequality.
  EXPECT_LT(CostEstimator::EstimateSelectivity(CompareOp::kEq),
            CostEstimator::EstimateSelectivity(CompareOp::kLt));
  EXPECT_LT(CostEstimator::EstimateSelectivity(CompareOp::kGt),
            CostEstimator::EstimateSelectivity(CompareOp::kNe));
}

TEST(CostEstimator, SeqScanGrowsWithTableSize) {
  const TableStats small{100, 2};
  const TableStats big{100000, 2000};
  EXPECT_LT(CostEstimator::SeqScan(small, 1.0).total(),
            CostEstimator::SeqScan(big, 1.0).total());
}

TEST(CostEstimator, PrefersIndexForHighlySelectivePredicate) {
  const TableStats big{1'000'000, 10'000};
  EXPECT_TRUE(CostEstimator::PreferIndex(big, 0.0001));
}

TEST(CostEstimator, PrefersSeqScanForAFullScan) {
  const TableStats big{1'000'000, 10'000};
  EXPECT_FALSE(CostEstimator::PreferIndex(big, 1.0));
}

TEST(CostEstimator, PrefersSeqScanForATinyTable) {
  // Scanning a single page beats random index I/O even when selective.
  const TableStats tiny{5, 1};
  EXPECT_FALSE(CostEstimator::PreferIndex(tiny, 0.01));
}

TEST(CostEstimator, TotalIsIoPlusCpu) {
  const TableStats s{1000, 50};
  const auto cost = CostEstimator::SeqScan(s, 0.5);
  EXPECT_DOUBLE_EQ(cost.total(), cost.io_cost + cost.cpu_cost);
}
