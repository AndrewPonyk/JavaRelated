#pragma once

#include <cstdint>
#include <string>
#include <vector>

#include "minidb/parser/ast.hpp"

/// @file plan_node.hpp
/// Physical plan produced by the planner and consumed by the executor. v1 plans
/// are single-node (no joins), so this is a flat struct rather than a tree.

namespace minidb {

enum class PlanType {
  kCreateTable,
  kInsert,
  kSeqScan,    ///< scan every tuple, applying the predicate
  kIndexScan,  ///< use the primary-key B+ Tree to satisfy the predicate
};

const char* PlanTypeToString(PlanType type);

struct PlanNode {
  PlanType type = PlanType::kSeqScan;
  std::string table;

  // Projection: which columns to output (empty => all).
  std::vector<std::string> output_columns;

  // Predicate to apply during a scan (may be absent).
  Predicate predicate;

  // For kIndexScan: the inclusive key range derived from the predicate.
  std::int64_t key_low = 0;
  std::int64_t key_high = 0;

  // Estimated cost the planner attached, surfaced via EXPLAIN / messages.
  double estimated_cost = 0.0;
};

}  // namespace minidb
