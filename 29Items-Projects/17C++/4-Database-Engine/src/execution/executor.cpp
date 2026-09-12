#include "minidb/execution/executor.hpp"

#include <algorithm>
#include <cstdio>
#include <limits>
#include <optional>
#include <string>
#include <type_traits>
#include <utility>
#include <variant>
#include <vector>

#include "minidb/catalog/tuple_codec.hpp"
#include "minidb/common/config.hpp"
#include "minidb/execution/cost_estimator.hpp"
#include "minidb/execution/plan_node.hpp"
#include "minidb/index/bplus_tree.hpp"
#include "minidb/storage/table_heap.hpp"

namespace minidb {
namespace {

bool IsIntegerType(TypeId type) {
  return type == TypeId::kInteger || type == TypeId::kBigInt ||
         type == TypeId::kBoolean;
}

bool ApplyCompare(int cmp, CompareOp op) {
  switch (op) {
    case CompareOp::kEq:
      return cmp == 0;
    case CompareOp::kNe:
      return cmp != 0;
    case CompareOp::kLt:
      return cmp < 0;
    case CompareOp::kLe:
      return cmp <= 0;
    case CompareOp::kGt:
      return cmp > 0;
    case CompareOp::kGe:
      return cmp >= 0;
  }
  return false;
}

StatusOr<Value> CoerceValue(const Value& v, TypeId target,
                            std::uint32_t max_len) {
  switch (target) {
    case TypeId::kInteger: {
      auto n = v.AsInt64();
      if (!n) return Status::InvalidArgument("expected an integer value");
      if (*n < std::numeric_limits<std::int32_t>::min() ||
          *n > std::numeric_limits<std::int32_t>::max()) {
        return Status::InvalidArgument("integer value out of INT range: " +
                                       std::to_string(*n));
      }
      return Value(static_cast<std::int32_t>(*n));
    }
    case TypeId::kBigInt: {
      auto n = v.AsInt64();
      if (!n) return Status::InvalidArgument("expected an integer value");
      return Value(static_cast<std::int64_t>(*n));
    }
    case TypeId::kBoolean: {
      auto n = v.AsInt64();
      if (!n) return Status::InvalidArgument("expected a boolean value");
      return Value(*n != 0);
    }
    case TypeId::kVarchar: {
      auto s = v.AsString();
      if (!s) return Status::InvalidArgument("expected a string value");
      if (max_len > 0 && s->size() > max_len) {
        return Status::InvalidArgument("value exceeds VARCHAR(" +
                                       std::to_string(max_len) + ")");
      }
      return Value(*s);
    }
    case TypeId::kInvalid:
      return Status::InvalidArgument("invalid target type");
  }
  return Status::Internal("unreachable coercion");
}

std::size_t EstimateRowBytes(const Schema& schema) {
  std::size_t bytes = 0;
  for (const auto& col : schema.columns()) {
    bytes += std::max<std::size_t>(1, col.fixed_size());
  }
  return std::max<std::size_t>(1, bytes);
}

TableStats MakeStats(std::size_t rows, const Schema& schema) {
  TableStats stats;
  stats.row_count = rows;
  const std::size_t row_bytes = EstimateRowBytes(schema);
  const std::size_t per_page = std::max<std::size_t>(1, PAGE_SIZE / row_bytes);
  stats.page_count = std::max<std::size_t>(1, (rows + per_page - 1) / per_page);
  return stats;
}

std::vector<Value> ProjectRow(const std::vector<Value>& row,
                              const std::vector<std::size_t>& projection) {
  std::vector<Value> out;
  out.reserve(projection.size());
  for (std::size_t i : projection) {
    out.push_back(i < row.size() ? row[i] : Value());
  }
  return out;
}

std::string FormatCost(double cost) {
  char buf[32];
  std::snprintf(buf, sizeof(buf), "%.2f", cost);
  return buf;
}

// Inclusive key range [lo, hi] that satisfies `column <op> key`. A lo > hi pair
// denotes the empty range.
std::pair<std::int64_t, std::int64_t> KeyRange(CompareOp op, std::int64_t key) {
  constexpr std::int64_t kMin = std::numeric_limits<std::int64_t>::min();
  constexpr std::int64_t kMax = std::numeric_limits<std::int64_t>::max();
  using Range = std::pair<std::int64_t, std::int64_t>;
  switch (op) {
    case CompareOp::kEq:
      return {key, key};
    case CompareOp::kGe:
      return {key, kMax};
    case CompareOp::kGt:
      return key == kMax ? Range{0, -1} : Range{key + 1, kMax};
    case CompareOp::kLe:
      return {kMin, key};
    case CompareOp::kLt:
      return key == kMin ? Range{0, -1} : Range{kMin, key - 1};
    case CompareOp::kNe:
      break;
  }
  return {0, -1};
}

}  // namespace

const char* PlanTypeToString(PlanType type) {
  switch (type) {
    case PlanType::kCreateTable:
      return "CreateTable";
    case PlanType::kInsert:
      return "Insert";
    case PlanType::kSeqScan:
      return "SeqScan";
    case PlanType::kIndexScan:
      return "IndexScan";
  }
  return "Unknown";
}

StatusOr<ResultSet> Executor::Execute(const Statement& statement) {
  return std::visit(
      [this](const auto& s) -> StatusOr<ResultSet> {
        using T = std::decay_t<decltype(s)>;
        if constexpr (std::is_same_v<T, CreateTableStatement>) {
          return ExecCreateTable(s);
        } else if constexpr (std::is_same_v<T, InsertStatement>) {
          return ExecInsert(s);
        } else {
          return ExecSelect(s);
        }
      },
      statement);
}

StatusOr<ResultSet> Executor::ExecCreateTable(
    const CreateTableStatement& stmt) {
  if (stmt.columns.empty()) {
    return Status::InvalidArgument("a table needs at least one column");
  }
  std::vector<Column> columns;
  columns.reserve(stmt.columns.size());
  for (const auto& def : stmt.columns) {
    if (def.type == TypeId::kInvalid) {
      return Status::InvalidArgument("column '" + def.name + "' has no type");
    }
    columns.push_back(Column{def.name, def.type, def.length});
  }

  auto oid = catalog_->CreateTable(stmt.table, Schema(std::move(columns)));
  if (!oid.ok()) return oid.status();

  ResultSet rs;
  rs.message = "Table '" + stmt.table +
               "' created (oid=" + std::to_string(oid.value()) + ").";
  return rs;
}

StatusOr<ResultSet> Executor::ExecInsert(const InsertStatement& stmt) {
  TableInfo* info = catalog_->GetTable(stmt.table);
  if (info == nullptr) return Status::NotFound("no such table: " + stmt.table);
  const Schema& schema = info->schema;

  TableHeap heap(bpm_, info->heap_first_page);
  const bool index_first =
      schema.column_count() > 0 && IsIntegerType(schema.column(0).type);
  BPlusTree index(bpm_, info->index_root_page);
  const page_id_t root_before = index.root_page_id();

  // Map each value position to a schema column index.
  std::vector<std::size_t> order;
  if (stmt.columns.empty()) {
    order.resize(schema.column_count());
    for (std::size_t i = 0; i < order.size(); ++i) order[i] = i;
  } else {
    for (const auto& name : stmt.columns) {
      auto idx = schema.GetColumnIndex(name);
      if (!idx) return Status::NotFound("no such column: " + name);
      order.push_back(*idx);
    }
  }

  std::size_t inserted = 0;
  for (const auto& tuple : stmt.rows) {
    if (tuple.size() != order.size()) {
      return Status::InvalidArgument(
          "value count " + std::to_string(tuple.size()) +
          " does not match column count " + std::to_string(order.size()));
    }
    std::vector<Value> row(schema.column_count());  // unspecified => NULL
    for (std::size_t i = 0; i < order.size(); ++i) {
      const Column& col = schema.column(order[i]);
      auto coerced = CoerceValue(tuple[i], col.type, col.length);
      if (!coerced.ok()) return coerced.status();
      row[order[i]] = std::move(coerced).value();
    }

    // Enforce primary-key uniqueness before touching the heap.
    std::optional<std::int64_t> key;
    if (index_first) {
      key = row[0].AsInt64();
      if (key && index.GetValue(*key).has_value()) {
        return Status::AlreadyExists("duplicate primary key " +
                                     std::to_string(*key) + " in '" +
                                     stmt.table + "'");
      }
    }

    auto rid = heap.InsertTuple(TupleCodec::Serialize(schema, row));
    if (!rid.ok()) return rid.status();

    if (index_first && key) {
      MINIDB_RETURN_IF_ERROR(index.Insert(*key, rid.value()));
    }
    ++inserted;
  }

  // Persist the index root if it changed (first insert / root split).
  if (index_first && index.root_page_id() != root_before) {
    MINIDB_RETURN_IF_ERROR(
        catalog_->SetIndexRoot(stmt.table, index.root_page_id()));
  }

  ResultSet rs;
  rs.affected_rows = inserted;
  rs.message = "INSERT " + std::to_string(inserted) + " row(s) into '" +
               stmt.table + "'.";
  return rs;
}

StatusOr<ResultSet> Executor::ExecSelect(const SelectStatement& stmt) {
  TableInfo* info = catalog_->GetTable(stmt.table);
  if (info == nullptr) return Status::NotFound("no such table: " + stmt.table);
  const Schema& schema = info->schema;
  TableHeap heap(bpm_, info->heap_first_page);

  // Resolve the projection.
  std::vector<std::size_t> projection;
  std::vector<std::string> headers;
  if (stmt.columns.empty()) {  // SELECT *
    for (std::size_t i = 0; i < schema.column_count(); ++i) {
      projection.push_back(i);
      headers.push_back(schema.column(i).name);
    }
  } else {
    for (const auto& name : stmt.columns) {
      auto idx = schema.GetColumnIndex(name);
      if (!idx) return Status::NotFound("no such column: " + name);
      projection.push_back(*idx);
      headers.push_back(name);
    }
  }

  // Prepare the predicate (validate column, coerce literal once).
  Predicate pred = stmt.where;
  std::optional<std::size_t> pred_idx;
  if (pred.present) {
    pred_idx = schema.GetColumnIndex(pred.column);
    if (!pred_idx) {
      return Status::NotFound("no such column in WHERE: " + pred.column);
    }
    const Column& pcol = schema.column(*pred_idx);
    auto coerced = CoerceValue(pred.literal, pcol.type, pcol.length);
    if (!coerced.ok()) return coerced.status();
    pred.literal = std::move(coerced).value();
  }

  // Plan: choose between sequential and index scan via the cost model.
  const TableStats stats = MakeStats(heap.CountTuples(), schema);
  const double selectivity =
      pred.present ? CostEstimator::EstimateSelectivity(pred.op) : 1.0;
  const bool index_applicable = pred.present && pred_idx && *pred_idx == 0 &&
                                IsIntegerType(schema.column(0).type) &&
                                pred.op != CompareOp::kNe &&
                                pred.literal.AsInt64().has_value() &&
                                info->index_root_page != INVALID_PAGE_ID;
  const bool use_index =
      index_applicable && CostEstimator::PreferIndex(stats, selectivity);
  const PlanType plan = use_index ? PlanType::kIndexScan : PlanType::kSeqScan;
  const double est_cost =
      use_index ? CostEstimator::IndexScan(stats, selectivity).total()
                : CostEstimator::SeqScan(stats, selectivity).total();

  // EXPLAIN: report the plan instead of running it.
  if (stmt.explain) {
    ResultSet rs;
    rs.columns = {"query plan"};
    rs.rows.push_back({Value(std::string(PlanTypeToString(plan)) + " on '" +
                             stmt.table + "'")});
    if (pred.present) {
      rs.rows.push_back({Value("  filter: " + pred.column + " " +
                               std::string(CompareOpToString(pred.op)) + " " +
                               pred.literal.ToString())});
    }
    rs.rows.push_back(
        {Value("  est_cost=" + FormatCost(est_cost) + " est_rows=" +
               std::to_string(static_cast<std::size_t>(std::max(
                   1.0, static_cast<double>(stats.row_count) * selectivity))) +
               " table_rows=" + std::to_string(stats.row_count))});
    return rs;
  }

  ResultSet rs;
  rs.columns = headers;

  if (use_index) {
    BPlusTree index(bpm_, info->index_root_page);
    const std::int64_t key = *pred.literal.AsInt64();
    const auto [lo, hi] = KeyRange(pred.op, key);
    for (const RID& rid : index.RangeScan(lo, hi)) {
      std::string bytes;
      if (heap.GetTuple(rid, &bytes)) {
        rs.rows.push_back(ProjectRow(
            TupleCodec::Deserialize(schema, bytes.data(), bytes.size()),
            projection));
      }
    }
  } else {
    for (const auto& [rid, bytes] : heap.Scan()) {
      (void)rid;
      const std::vector<Value> row =
          TupleCodec::Deserialize(schema, bytes.data(), bytes.size());
      if (pred.present) {
        const int cmp = CompareValues(row[*pred_idx], pred.literal);
        if (!ApplyCompare(cmp, pred.op)) continue;
      }
      rs.rows.push_back(ProjectRow(row, projection));
    }
  }

  rs.message = std::string("scan=") + PlanTypeToString(plan) +
               " est_cost=" + FormatCost(est_cost) +
               " rows=" + std::to_string(rs.rows.size());
  return rs;
}

}  // namespace minidb
