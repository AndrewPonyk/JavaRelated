#include "minidb/engine/database.hpp"

#include <cstdlib>

#include "minidb/common/config.hpp"
#include "minidb/parser/lexer.hpp"
#include "minidb/parser/parser.hpp"
#include "minidb/storage/header_page.hpp"
#include "minidb/storage/table_heap.hpp"

namespace minidb {
namespace {

/// Buffer pool size in frames, from MINIDB_BUFFER_POOL_PAGES if set and valid,
/// otherwise the compile-time default. A pool smaller than 2 frames cannot make
/// progress, so the value is clamped up.
std::size_t BufferPoolPagesFromEnv() {
  const char* raw = std::getenv("MINIDB_BUFFER_POOL_PAGES");
  if (raw == nullptr) return DEFAULT_BUFFER_POOL_PAGES;
  char* end = nullptr;
  const long parsed = std::strtol(raw, &end, 10);
  if (end == raw || parsed < 2) return DEFAULT_BUFFER_POOL_PAGES;
  return static_cast<std::size_t>(parsed);
}

}  // namespace

StatusOr<Database> Database::Open(const std::string& path) {
  Database db;

  db.disk_ = std::make_unique<DiskManager>();
  MINIDB_RETURN_IF_ERROR(db.disk_->Open(path));

  db.buffer_pool_ = std::make_unique<BufferPoolManager>(
      BufferPoolPagesFromEnv(), db.disk_.get());

  // Read or initialize the superblock (page 0).
  Page* header = db.buffer_pool_->FetchPage(0);
  if (header == nullptr) return Status::IOError("cannot read header page");
  Superblock sb;
  ReadSuperblock(header->data(), &sb);
  if (!SuperblockValid(sb)) {
    // Fresh database: stamp the superblock and allocate the catalog heap.
    sb = Superblock{};
    auto catalog_root = TableHeap::CreateFirstPage(db.buffer_pool_.get());
    if (!catalog_root.ok()) {
      db.buffer_pool_->UnpinPage(0, false);
      return catalog_root.status();
    }
    sb.catalog_root_page = catalog_root.value();
    WriteSuperblock(header->data(), sb);
    db.buffer_pool_->UnpinPage(0, /*is_dirty=*/true);
  } else {
    db.buffer_pool_->UnpinPage(0, /*is_dirty=*/false);
  }

  db.catalog_ =
      std::make_unique<Catalog>(db.buffer_pool_.get(), sb.catalog_root_page);
  MINIDB_RETURN_IF_ERROR(db.catalog_->Load());

  db.executor_ =
      std::make_unique<Executor>(db.catalog_.get(), db.buffer_pool_.get());
  return db;
}

StatusOr<ResultSet> Database::Execute(const std::string& sql) {
  Lexer lexer(sql);
  auto tokens = lexer.Tokenize();
  if (!tokens.ok()) return tokens.status();

  // An empty or comment-only input tokenizes to just EOF: treat it as a no-op
  // (empty message => the CLI prints nothing) rather than a syntax error, so
  // scripts with blank/comment lines run clean.
  if (tokens.value().size() <= 1) {
    return ResultSet{};
  }

  Parser parser(std::move(tokens).value());
  auto statement = parser.Parse();
  if (!statement.ok()) return statement.status();

  return executor_->Execute(statement.value());
}

Status Database::Flush() {
  return buffer_pool_->FlushAll();
}

}  // namespace minidb
