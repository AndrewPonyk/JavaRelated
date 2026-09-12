/**
 * @file test_fragmentation.c
 * @brief Tests for the fragmentation metric and coalescing behaviour.
 */
#include "test_framework.h"
#include "memalloc.h"
#include "debug/stats.h"

static int test_fragmentation_in_range(void) {
    ASSERT_EQ(mem_init(MEM_FIRST_FIT), 0);

    void *blocks[8];
    for (int i = 0; i < 8; i++) {
        blocks[i] = mem_malloc(64);
        ASSERT_NOT_NULL(blocks[i]);
    }
    /* Free every other block to punch holes into the heap. */
    for (int i = 0; i < 8; i += 2) {
        mem_free(blocks[i]);
    }

    double frag = stats_fragmentation();
    ASSERT_TRUE(frag >= 0.0 && frag <= 1.0);

    for (int i = 1; i < 8; i += 2) {
        mem_free(blocks[i]);
    }
    mem_destroy();
    return 0;
}

static int test_stats_counters(void) {
    ASSERT_EQ(mem_init(MEM_FIRST_FIT), 0);

    void *a = mem_malloc(100);
    void *b = mem_malloc(200);
    ASSERT_NOT_NULL(a);
    ASSERT_NOT_NULL(b);

    mem_stats_t s = stats_get();
    ASSERT_EQ(s.total_allocs, 2u);
    ASSERT_EQ(s.live_blocks, 2u);
    ASSERT_TRUE(s.peak_bytes >= 300u);

    mem_free(a);
    s = stats_get();
    ASSERT_EQ(s.total_frees, 1u);
    ASSERT_EQ(s.live_blocks, 1u);

    mem_free(b);
    mem_destroy();
    return 0;
}

int main(void) {
    RUN_TEST(test_fragmentation_in_range);
    RUN_TEST(test_stats_counters);
    return TEST_SUMMARY();
}
