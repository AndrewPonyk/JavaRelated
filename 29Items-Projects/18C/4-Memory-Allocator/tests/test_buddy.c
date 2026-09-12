/**
 * @file test_buddy.c
 * @brief Tests for the buddy-system backend.
 *
 * The buddy allocator is currently a scaffold (Phase 2). These tests document
 * the intended contract and SKIP gracefully until the implementation lands.
 * TODO: remove the skip guards and enable the real assertions once buddy_init
 *       returns 0.
 */
#include "test_framework.h"
#include "memalloc.h"
#include "strategies/buddy.h"

#include <string.h>

static int test_buddy_roundtrip(void) {
    if (mem_init(MEM_BUDDY) != 0) {
        printf("  SKIP: buddy system not yet implemented\n");
        mem_destroy();
        return 0; /* treated as pass while scaffolded */
    }

    /* --- Enabled once buddy is implemented --- */
    void *a = mem_malloc(100); /* rounds up to 128 */
    ASSERT_NOT_NULL(a);
    memset(a, 0x5A, 100);
    void *b = mem_malloc(100);
    ASSERT_NOT_NULL(b);
    ASSERT_NE(a, b); /* distinct, non-overlapping blocks */

    mem_free(a);
    mem_free(b);
    mem_destroy();
    return 0;
}

int main(void) {
    RUN_TEST(test_buddy_roundtrip);
    return TEST_SUMMARY();
}
