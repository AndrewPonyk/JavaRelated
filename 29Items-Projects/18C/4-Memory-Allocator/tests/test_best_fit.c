/**
 * @file test_best_fit.c
 * @brief Unit tests for best-fit placement semantics.
 */
#include "test_framework.h"
#include "memalloc.h"

#include <string.h>

/*
 * Lay out blocks contiguously with allocated "spacers" between the holes so
 * that freeing the targets does NOT coalesce them away:
 *
 *   [a][small][b][big][c][ ...trailing free... ]
 *
 * After freeing `small` (64B hole) and `big` (256B hole), a 64B request must
 * pick the tightest hole — `small` — not the 256B hole nor the huge tail.
 */
static int test_best_fit_picks_tightest(void) {
    ASSERT_EQ(mem_init(MEM_BEST_FIT), 0);

    void *a     = mem_malloc(64);
    void *small = mem_malloc(64);
    void *b     = mem_malloc(64);
    void *big   = mem_malloc(256);
    void *c     = mem_malloc(64);
    ASSERT_NOT_NULL(a);
    ASSERT_NOT_NULL(small);
    ASSERT_NOT_NULL(b);
    ASSERT_NOT_NULL(big);
    ASSERT_NOT_NULL(c);

    mem_free(small); /* 64B hole, bounded by allocated a and b  */
    mem_free(big);   /* 256B hole, bounded by allocated b and c */

    void *p = mem_malloc(64);
    ASSERT_NOT_NULL(p);
    ASSERT_EQ(p, small); /* tightest fit reused */

    mem_free(p);
    mem_free(a);
    mem_free(b);
    mem_free(c);
    mem_destroy();
    return 0;
}

static int test_strategy_switch(void) {
    ASSERT_EQ(mem_init(MEM_BEST_FIT), 0);
    ASSERT_EQ(mem_get_strategy(), MEM_BEST_FIT);
    mem_set_strategy(MEM_FIRST_FIT);
    ASSERT_EQ(mem_get_strategy(), MEM_FIRST_FIT);
    void *p = mem_malloc(32);
    ASSERT_NOT_NULL(p);
    mem_free(p);
    mem_destroy();
    return 0;
}

int main(void) {
    RUN_TEST(test_best_fit_picks_tightest);
    RUN_TEST(test_strategy_switch);
    return TEST_SUMMARY();
}
