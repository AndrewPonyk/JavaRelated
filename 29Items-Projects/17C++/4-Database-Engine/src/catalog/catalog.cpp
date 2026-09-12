#include "minidb/catalog/catalog.hpp"

#include <algorithm>
#include <cstdint>
#include <string>
#include <vector>

#include "minidb/catalog/column.hpp"
#include "minidb/common/byte_io.hpp"
#include "minidb/storage/table_heap.hpp"

namespace minidb {
namespace {

void AppendU16(std::string& b, std::uint16_t v) {
  char t[2];
  StoreU16(t, v);
  b.append(t, sizeof(t));
}
void AppendU32(std::string& b, std::uint32_t v) {
  char t[4];
  StoreU32(t, v);
  b.append(t, sizeof(t));
}
void AppendI32(std::string& b, std::int32_t v) {
  char t[4];
  StoreI32(t, v);
  b.append(t, sizeof(t));
}

std::string SerializeTableInfo(const TableInfo& info) {
  std::string out;
  AppendU32(out, info.oid);
  AppendI32(out, info.heap_first_page);
  AppendI32(out, info.index_root_page);
  AppendU16(out, static_cast<std::uint16_t>(info.name.size()));
  out.append(info.name);

  const auto& cols = info.schema.columns();
  AppendU16(out, static_cast<std::uint16_t>(cols.size()));
  for (const auto& col : cols) {
    out.push_back(static_cast<char>(static_cast<std::uint8_t>(col.type)));
    AppendU32(out, col.length);
    AppendU16(out, static_cast<std::uint16_t>(col.name.size()));
    out.append(col.name);
  }
  return out;
}

TableInfo DeserializeTableInfo(const std::string& s) {
  TableInfo info;
  std::size_t pos = 0;
  info.oid = LoadU32(s.data() + pos);
  pos += 4;
  info.heap_first_page = LoadI32(s.data() + pos);
  pos += 4;
  info.index_root_page = LoadI32(s.data() + pos);
  pos += 4;
  const std::uint16_t name_len = LoadU16(s.data() + pos);
  pos += 2;
  info.name.assign(s.data() + pos, name_len);
  pos += name_len;

  const std::uint16_t ncols = LoadU16(s.data() + pos);
  pos += 2;
  std::vector<Column> cols;
  cols.reserve(ncols);
  for (std::uint16_t i = 0; i < ncols; ++i) {
    Column col;
    col.type = static_cast<TypeId>(static_cast<std::uint8_t>(s[pos]));
    pos += 1;
    col.length = LoadU32(s.data() + pos);
    pos += 4;
    const std::uint16_t cn = LoadU16(s.data() + pos);
    pos += 2;
    col.name.assign(s.data() + pos, cn);
    pos += cn;
    cols.push_back(std::move(col));
  }
  info.schema = Schema(std::move(cols));
  return info;
}

}  // namespace

Status Catalog::Load() {
  tables_.clear();
  TableHeap heap(bpm_, catalog_first_page_);
  oid_t max_oid = 0;
  for (const auto& [rid, bytes] : heap.Scan()) {
    (void)rid;
    TableInfo info = DeserializeTableInfo(bytes);
    max_oid = std::max(max_oid, info.oid);
    tables_.emplace(info.name, std::move(info));
  }
  next_oid_ = max_oid + 1;
  return Status::Ok();
}

Status Catalog::Save() const {
  TableHeap heap(bpm_, catalog_first_page_);
  MINIDB_RETURN_IF_ERROR(heap.Clear());

  // Deterministic order keeps the on-disk layout stable across rewrites.
  std::vector<const TableInfo*> ordered;
  ordered.reserve(tables_.size());
  for (const auto& [name, info] : tables_) {
    (void)name;
    ordered.push_back(&info);
  }
  std::sort(
      ordered.begin(), ordered.end(),
      [](const TableInfo* a, const TableInfo* b) { return a->oid < b->oid; });
  for (const TableInfo* info : ordered) {
    auto rid = heap.InsertTuple(SerializeTableInfo(*info));
    if (!rid.ok()) return rid.status();
  }
  return Status::Ok();
}

StatusOr<oid_t> Catalog::CreateTable(const std::string& name, Schema schema) {
  if (name.empty()) {
    return Status::InvalidArgument("table name must not be empty");
  }
  if (Contains(name)) {
    return Status::AlreadyExists("table already exists: " + name);
  }

  auto heap_first = TableHeap::CreateFirstPage(bpm_);
  if (!heap_first.ok()) return heap_first.status();

  TableInfo info;
  info.oid = next_oid_++;
  info.name = name;
  info.schema = std::move(schema);
  info.heap_first_page = heap_first.value();
  info.index_root_page = INVALID_PAGE_ID;
  tables_.emplace(name, std::move(info));

  MINIDB_RETURN_IF_ERROR(Save());
  return tables_.at(name).oid;
}

Status Catalog::SetIndexRoot(const std::string& name, page_id_t root) {
  auto it = tables_.find(name);
  if (it == tables_.end()) return Status::NotFound("no such table: " + name);
  it->second.index_root_page = root;
  return Save();
}

TableInfo* Catalog::GetTable(const std::string& name) {
  auto it = tables_.find(name);
  return it == tables_.end() ? nullptr : &it->second;
}

const TableInfo* Catalog::GetTable(const std::string& name) const {
  auto it = tables_.find(name);
  return it == tables_.end() ? nullptr : &it->second;
}

std::vector<std::string> Catalog::TableNames() const {
  std::vector<std::string> names;
  names.reserve(tables_.size());
  for (const auto& [name, info] : tables_) {
    (void)info;
    names.push_back(name);
  }
  std::sort(names.begin(), names.end());
  return names;
}

}  // namespace minidb
