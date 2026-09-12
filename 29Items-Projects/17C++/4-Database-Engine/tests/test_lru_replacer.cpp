// Unit tests for the LRU buffer-pool replacer. The replacer is fully
// implemented, so these tests are expected to pass as-is.

#include <gtest/gtest.h>

#include "minidb/storage/lru_replacer.hpp"

using minidb::frame_id_t;
using minidb::LRUReplacer;

TEST(LRUReplacer, EmptyHasNoVictim) {
  LRUReplacer r(4);
  frame_id_t victim = -1;
  EXPECT_FALSE(r.Victim(&victim));
  EXPECT_EQ(r.Size(), 0u);
}

TEST(LRUReplacer, VictimFollowsUnpinOrder) {
  LRUReplacer r(7);
  for (frame_id_t f : {1, 2, 3, 4, 5, 6}) r.Unpin(f);
  r.Unpin(1);  // already evictable -> ignored, keeps original position
  EXPECT_EQ(r.Size(), 6u);

  frame_id_t v = 0;
  ASSERT_TRUE(r.Victim(&v));
  EXPECT_EQ(v, 1);
  ASSERT_TRUE(r.Victim(&v));
  EXPECT_EQ(v, 2);
  ASSERT_TRUE(r.Victim(&v));
  EXPECT_EQ(v, 3);
  EXPECT_EQ(r.Size(), 3u);
}

TEST(LRUReplacer, PinRemovesFromEvictionSet) {
  LRUReplacer r(7);
  for (frame_id_t f : {1, 2, 3, 4, 5, 6}) r.Unpin(f);

  frame_id_t v = 0;
  r.Victim(&v);  // evicts 1
  r.Victim(&v);  // evicts 2
  r.Victim(&v);  // evicts 3

  r.Pin(3);  // no-op: already evicted
  r.Pin(4);  // removes 4 from the eviction set
  EXPECT_EQ(r.Size(), 2u);

  r.Unpin(4);  // re-add 4 as most-recently-used
  ASSERT_TRUE(r.Victim(&v));
  EXPECT_EQ(v, 5);
  ASSERT_TRUE(r.Victim(&v));
  EXPECT_EQ(v, 6);
  ASSERT_TRUE(r.Victim(&v));
  EXPECT_EQ(v, 4);
  EXPECT_FALSE(r.Victim(&v));
}

TEST(LRUReplacer, RespectsCapacity) {
  LRUReplacer r(2);
  r.Unpin(0);
  r.Unpin(1);
  r.Unpin(2);  // refused: would exceed capacity
  EXPECT_EQ(r.Size(), 2u);
}

TEST(LRUReplacer, PinUnknownFrameIsNoOp) {
  LRUReplacer r(4);
  r.Pin(99);  // not tracked; must not crash or change size
  EXPECT_EQ(r.Size(), 0u);
}
