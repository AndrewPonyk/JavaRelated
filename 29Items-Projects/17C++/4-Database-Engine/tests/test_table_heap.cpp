// Tests for the page-chained, buffer-pool-backed tuple heap.

#include <gtest/gtest.h>

#include <cstdint>
#include <filesystem>
#include <memory>
#include <string>

#include "minidb/storage/buffer_pool_manager.hpp"
#include "minidb/storage/disk_manager.hpp"
#include "minidb/storage/table_heap.hpp"

using minidb::BufferPoolManager;
using minidb::DiskManager;
using minidb::page_id_t;
using minidb::RID;
using minidb::TableHeap;

namespace {
class TableHeapTest : public ::testing::Test {
 protected:
  void SetUp() override {
    path_ = (std::filesystem::temp_directory_path() /
             ("minidb_heap_" +
              std::to_string(reinterpret_cast<std::uintptr_t>(this)) + ".db"))
                .string();
    std::error_code ec;
    std::filesystem::remove(path_, ec);
  }
  void TearDown() override {
    std::error_code ec;
    std::filesystem::remove(path_, ec);
  }
  std::string path_;
};
}  // namespace

TEST_F(TableHeapTest, InsertGetAndScan) {
  DiskManager disk;
  ASSERT_TRUE(disk.Open(path_).ok());
  BufferPoolManager bpm(16, &disk);
  auto first = TableHeap::CreateFirstPage(&bpm);
  ASSERT_TRUE(first.ok());
  TableHeap heap(&bpm, first.value());

  auto r1 = heap.InsertTuple("alpha");
  auto r2 = heap.InsertTuple("beta");
  ASSERT_TRUE(r1.ok());
  ASSERT_TRUE(r2.ok());

  std::string got;
  ASSERT_TRUE(heap.GetTuple(r1.value(), &got));
  EXPECT_EQ(got, "alpha");
  ASSERT_TRUE(heap.GetTuple(r2.value(), &got));
  EXPECT_EQ(got, "beta");

  EXPECT_EQ(heap.CountTuples(), 2u);
  EXPECT_EQ(heap.Scan().size(), 2u);
}

TEST_F(TableHeapTest, GrowsAcrossManyPages) {
  DiskManager disk;
  ASSERT_TRUE(disk.Open(path_).ok());
  BufferPoolManager bpm(32, &disk);
  auto first = TableHeap::CreateFirstPage(&bpm);
  ASSERT_TRUE(first.ok());
  TableHeap heap(&bpm, first.value());

  constexpr int kN = 2000;
  for (int i = 0; i < kN; ++i) {
    ASSERT_TRUE(heap.InsertTuple("tuple-" + std::to_string(i)).ok());
  }
  EXPECT_EQ(heap.CountTuples(), static_cast<std::size_t>(kN));
  EXPECT_EQ(heap.Scan().size(), static_cast<std::size_t>(kN));
}

TEST_F(TableHeapTest, PersistsAcrossReopen) {
  page_id_t first_page = -1;
  RID rid{};
  {
    DiskManager disk;
    ASSERT_TRUE(disk.Open(path_).ok());
    BufferPoolManager bpm(16, &disk);
    auto first = TableHeap::CreateFirstPage(&bpm);
    ASSERT_TRUE(first.ok());
    first_page = first.value();
    TableHeap heap(&bpm, first_page);
    auto r = heap.InsertTuple("durable-tuple");
    ASSERT_TRUE(r.ok());
    rid = r.value();
    ASSERT_TRUE(bpm.FlushAll().ok());
  }
  {
    DiskManager disk;
    ASSERT_TRUE(disk.Open(path_).ok());
    BufferPoolManager bpm(16, &disk);
    TableHeap heap(&bpm, first_page);
    std::string got;
    ASSERT_TRUE(heap.GetTuple(rid, &got));
    EXPECT_EQ(got, "durable-tuple");
    EXPECT_EQ(heap.CountTuples(), 1u);
  }
}

TEST_F(TableHeapTest, ClearEmptiesButKeepsChain) {
  DiskManager disk;
  ASSERT_TRUE(disk.Open(path_).ok());
  BufferPoolManager bpm(16, &disk);
  auto first = TableHeap::CreateFirstPage(&bpm);
  ASSERT_TRUE(first.ok());
  TableHeap heap(&bpm, first.value());

  for (int i = 0; i < 50; ++i) {
    ASSERT_TRUE(heap.InsertTuple("x" + std::to_string(i)).ok());
  }
  ASSERT_TRUE(heap.Clear().ok());
  EXPECT_EQ(heap.CountTuples(), 0u);
  // Reusable after clear.
  ASSERT_TRUE(heap.InsertTuple("again").ok());
  EXPECT_EQ(heap.CountTuples(), 1u);
}
