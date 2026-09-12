#pragma once

#include <string>
#include <unordered_map>
#include <vector>

#include "minidb/catalog/schema.hpp"
#include "minidb/common/status.hpp"
#include "minidb/storage/buffer_pool_manager.hpp"

/// @file catalog.hpp
/// The system catalog: a registry of tables, their schemas, and the page ids of
/// each table's heap and primary-key B+ Tree root. The catalog persists itself
/// to a dedicated heap (rooted at a page recorded in the superblock) so that
/// schemas survive a reopen.

namespace minidb {

struct TableInfo {
  oid_t oid = INVALID_OID;
  std::string name;
  Schema schema;
  page_id_t heap_first_page =
      INVALID_PAGE_ID;  ///< first page of the table heap
  page_id_t index_root_page = INVALID_PAGE_ID;  ///< primary-key B+ Tree root
};

class Catalog {
 public:
  /// @param bpm        buffer pool to allocate/read catalog + table pages from
  /// @param catalog_first_page  first page of the catalog's own heap
  Catalog(BufferPoolManager* bpm, page_id_t catalog_first_page)
      : bpm_(bpm), catalog_first_page_(catalog_first_page) {}

  /// Rebuilds the in-memory registry from the on-disk catalog heap.
  Status Load();

  /// Registers a new table (allocating its heap) and persists the catalog.
  StatusOr<oid_t> CreateTable(const std::string& name, Schema schema);

  /// Updates a table's index root page and persists the change.
  Status SetIndexRoot(const std::string& name, page_id_t root);

  TableInfo* GetTable(const std::string& name);
  const TableInfo* GetTable(const std::string& name) const;
  bool Contains(const std::string& name) const {
    return tables_.find(name) != tables_.end();
  }
  std::vector<std::string> TableNames() const;

 private:
  Status Save() const;  ///< rewrite the catalog heap from `tables_`

  BufferPoolManager* bpm_;
  page_id_t catalog_first_page_;
  std::unordered_map<std::string, TableInfo> tables_;
  oid_t next_oid_ = 1;
};

}  // namespace minidb
