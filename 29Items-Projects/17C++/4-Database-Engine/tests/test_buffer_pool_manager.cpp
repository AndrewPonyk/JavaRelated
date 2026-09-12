// Integration tests for the storage stack: mmap DiskManager + BufferPoolManager
// + LRUReplacer working together against a real temp file on disk.

#include <gtest/gtest.h>

#include <cstring>
#include <filesystem>
#include <string>

#include "minidb/storage/buffer_pool_manager.hpp"
#include "minidb/storage/disk_manager.hpp"
#include "minidb/storage/page.hpp"

using minidb::BufferPoolManager;
using minidb::DiskManager;
using minidb::Page;
using minidb::page_id_t;

namespace {
class BufferPoolTest : public ::testing::Test {
 protected:
  void SetUp() override {
    path_ = (std::filesystem::temp_directory_path() /
             ("minidb_bpm_" +
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

TEST_F(BufferPoolTest, NewPageRoundTripsThroughTheCache) {
  DiskManager disk;
  ASSERT_TRUE(disk.Open(path_).ok());
  BufferPoolManager bpm(8, &disk);

  page_id_t pid = -1;
  Page* p = bpm.NewPage(&pid);
  ASSERT_NE(p, nullptr);
  const char* msg = "hello-buffer-pool";
  std::memcpy(p->data(), msg, std::strlen(msg) + 1);
  EXPECT_TRUE(bpm.UnpinPage(pid, /*is_dirty=*/true));

  Page* again = bpm.FetchPage(pid);
  ASSERT_NE(again, nullptr);
  EXPECT_STREQ(again->data(), msg);
  EXPECT_TRUE(bpm.UnpinPage(pid, /*is_dirty=*/false));
}

TEST_F(BufferPoolTest, DataPersistsAcrossReopen) {
  page_id_t pid = -1;
  {
    DiskManager disk;
    ASSERT_TRUE(disk.Open(path_).ok());
    BufferPoolManager bpm(4, &disk);
    Page* p = bpm.NewPage(&pid);
    ASSERT_NE(p, nullptr);
    std::strcpy(p->data(), "durable");
    EXPECT_TRUE(bpm.UnpinPage(pid, true));
    EXPECT_TRUE(bpm.FlushAll().ok());
  }
  {
    DiskManager disk;
    ASSERT_TRUE(disk.Open(path_).ok());
    BufferPoolManager bpm(4, &disk);
    Page* p = bpm.FetchPage(pid);
    ASSERT_NE(p, nullptr);
    EXPECT_STREQ(p->data(), "durable");
    EXPECT_TRUE(bpm.UnpinPage(pid, false));
  }
}

TEST_F(BufferPoolTest, FullPoolOfPinnedPagesRefusesNewPage) {
  DiskManager disk;
  ASSERT_TRUE(disk.Open(path_).ok());
  BufferPoolManager bpm(2, &disk);

  page_id_t a = -1;
  page_id_t b = -1;
  ASSERT_NE(bpm.NewPage(&a), nullptr);
  ASSERT_NE(bpm.NewPage(&b), nullptr);

  page_id_t c = -1;
  EXPECT_EQ(bpm.NewPage(&c), nullptr);  // both frames pinned

  EXPECT_TRUE(bpm.UnpinPage(a, false));
  EXPECT_NE(bpm.NewPage(&c), nullptr);  // a frame became evictable
  EXPECT_TRUE(bpm.UnpinPage(b, false));
  EXPECT_TRUE(bpm.UnpinPage(c, false));
}

TEST_F(BufferPoolTest, EvictionWritesBackDirtyPage) {
  DiskManager disk;
  ASSERT_TRUE(disk.Open(path_).ok());
  BufferPoolManager bpm(1, &disk);  // single frame forces an eviction

  page_id_t p0 = -1;
  Page* a = bpm.NewPage(&p0);
  ASSERT_NE(a, nullptr);
  std::strcpy(a->data(), "first");
  ASSERT_TRUE(bpm.UnpinPage(p0, /*is_dirty=*/true));

  page_id_t p1 = -1;
  Page* b = bpm.NewPage(&p1);  // evicts p0, writing it back to disk
  ASSERT_NE(b, nullptr);
  ASSERT_TRUE(bpm.UnpinPage(p1, false));

  Page* back = bpm.FetchPage(p0);  // must re-read p0 from disk
  ASSERT_NE(back, nullptr);
  EXPECT_STREQ(back->data(), "first");
  EXPECT_TRUE(bpm.UnpinPage(p0, false));
}
