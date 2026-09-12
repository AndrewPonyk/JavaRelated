// Tests for the disk-backed B+ Tree. A deliberately small fan-out forces
// multi-level splits with only a few thousand keys, and a std::map oracle
// validates structural correctness under randomized insert/erase.

#include <gtest/gtest.h>

#include <algorithm>
#include <cstdint>
#include <filesystem>
#include <map>
#include <memory>
#include <numeric>
#include <random>
#include <string>
#include <vector>

#include "minidb/index/bplus_tree.hpp"
#include "minidb/storage/buffer_pool_manager.hpp"
#include "minidb/storage/disk_manager.hpp"

using minidb::BPlusTree;
using minidb::BufferPoolManager;
using minidb::DiskManager;
using minidb::INVALID_PAGE_ID;
using minidb::page_id_t;
using minidb::RID;
using minidb::slot_id_t;
using minidb::StatusCode;

namespace {
RID Rid(std::int64_t slot) {
  RID r;
  r.page_id = 1;
  r.slot = static_cast<slot_id_t>(slot);
  return r;
}

class BPlusTreeTest : public ::testing::Test {
 protected:
  void SetUp() override {
    path_ = (std::filesystem::temp_directory_path() /
             ("minidb_bpt_" +
              std::to_string(reinterpret_cast<std::uintptr_t>(this)) + ".db"))
                .string();
    std::error_code ec;
    std::filesystem::remove(path_, ec);
    disk_ = std::make_unique<DiskManager>();
    ASSERT_TRUE(disk_->Open(path_).ok());
    bpm_ = std::make_unique<BufferPoolManager>(256, disk_.get());
  }
  void TearDown() override {
    bpm_.reset();
    disk_.reset();
    std::error_code ec;
    std::filesystem::remove(path_, ec);
  }
  std::string path_;
  std::unique_ptr<DiskManager> disk_;
  std::unique_ptr<BufferPoolManager> bpm_;
};
}  // namespace

TEST_F(BPlusTreeTest, PointInsertAndLookup) {
  BPlusTree tree(bpm_.get());
  EXPECT_TRUE(tree.empty());
  ASSERT_TRUE(tree.Insert(10, Rid(1)).ok());
  ASSERT_TRUE(tree.Insert(20, Rid(2)).ok());
  EXPECT_EQ(tree.size(), 2u);

  auto v = tree.GetValue(10);
  ASSERT_TRUE(v.has_value());
  EXPECT_EQ(v->slot, 1);
  EXPECT_FALSE(tree.GetValue(99).has_value());
}

TEST_F(BPlusTreeTest, DuplicateKeyRejected) {
  BPlusTree tree(bpm_.get());
  ASSERT_TRUE(tree.Insert(1, Rid(1)).ok());
  const auto s = tree.Insert(1, Rid(2));
  EXPECT_FALSE(s.ok());
  EXPECT_EQ(s.code(), StatusCode::kAlreadyExists);
}

TEST_F(BPlusTreeTest, EraseRemovesKey) {
  BPlusTree tree(bpm_.get());
  ASSERT_TRUE(tree.Insert(5, Rid(5)).ok());
  EXPECT_TRUE(tree.Erase(5).ok());
  EXPECT_FALSE(tree.GetValue(5).has_value());
  EXPECT_EQ(tree.Erase(5).code(), StatusCode::kNotFound);
}

TEST_F(BPlusTreeTest, RangeScanIsInclusiveAndOrdered) {
  BPlusTree tree(bpm_.get(), INVALID_PAGE_ID, /*leaf_max=*/4,
                 /*internal_max=*/4);
  for (int k : {50, 10, 30, 20, 40, 5, 60, 15}) {
    ASSERT_TRUE(tree.Insert(k, Rid(k)).ok());
  }
  const auto rids = tree.RangeScan(15, 40);  // 15, 20, 30, 40
  ASSERT_EQ(rids.size(), 4u);
  EXPECT_EQ(rids[0].slot, 15);
  EXPECT_EQ(rids[1].slot, 20);
  EXPECT_EQ(rids[2].slot, 30);
  EXPECT_EQ(rids[3].slot, 40);
  EXPECT_TRUE(tree.RangeScan(40, 15).empty());  // inverted -> empty
}

TEST_F(BPlusTreeTest, SplitsAndDeletesMatchOracle) {
  // Small fan-out => the 2000-key workload builds a multi-level tree.
  BPlusTree tree(bpm_.get(), INVALID_PAGE_ID, /*leaf_max=*/4,
                 /*internal_max=*/4);
  std::map<std::int64_t, int> oracle;

  std::vector<int> keys(2000);
  std::iota(keys.begin(), keys.end(), 0);
  std::mt19937 rng(2026);
  std::shuffle(keys.begin(), keys.end(), rng);

  for (int k : keys) {
    ASSERT_TRUE(tree.Insert(k, Rid(k)).ok());
    oracle[k] = k;
  }
  EXPECT_EQ(tree.size(), oracle.size());
  for (int k = 0; k < 2000; ++k) {
    ASSERT_TRUE(tree.GetValue(k).has_value());
  }

  std::shuffle(keys.begin(), keys.end(), rng);
  for (int i = 0; i < 700; ++i) {
    ASSERT_TRUE(tree.Erase(keys[i]).ok());
    oracle.erase(keys[i]);
  }
  EXPECT_EQ(tree.size(), oracle.size());

  const std::int64_t lo = 400;
  const std::int64_t hi = 1600;
  std::vector<std::int64_t> want;
  for (const auto& [k, v] : oracle) {
    if (k >= lo && k <= hi) want.push_back(k);
  }
  const auto got = tree.RangeScan(lo, hi);
  ASSERT_EQ(got.size(), want.size());
  for (std::size_t i = 0; i < want.size(); ++i) {
    EXPECT_EQ(static_cast<std::int64_t>(got[i].slot), want[i]);
  }
}

TEST_F(BPlusTreeTest, PersistsAcrossReopen) {
  bpm_.reset();  // release the fixture's handle so we can reopen the file
  disk_.reset();

  const std::vector<std::int64_t> ks = {50, 10, 30, 20, 40, 5, 60, 15, 25, 35};
  page_id_t root = INVALID_PAGE_ID;
  {
    DiskManager disk;
    ASSERT_TRUE(disk.Open(path_).ok());
    BufferPoolManager bpm(64, &disk);
    BPlusTree tree(&bpm, INVALID_PAGE_ID, 4, 4);
    for (auto k : ks) ASSERT_TRUE(tree.Insert(k, Rid(k)).ok());
    root = tree.root_page_id();
    ASSERT_TRUE(bpm.FlushAll().ok());
  }
  {
    DiskManager disk;
    ASSERT_TRUE(disk.Open(path_).ok());
    BufferPoolManager bpm(64, &disk);
    BPlusTree tree(&bpm, root, 4, 4);
    EXPECT_EQ(tree.size(), ks.size());
    for (auto k : ks) EXPECT_TRUE(tree.GetValue(k).has_value());
    EXPECT_EQ(tree.RangeScan(15, 40).size(), 6u);  // 15,20,25,30,35,40
  }
}
