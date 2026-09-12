/* =============================================================================
 *  test_heap.c  --  Host unit tests for the kmalloc/kfree heap
 *
 *  Compiled with kernel/mm/memory.c. The heap is initialized over a malloc'd
 *  arena (heap_init_at) so it runs safely in a hosted process. Verifies basic
 *  allocation, reclamation, full coalescing, and exhaustion.
 * ===========================================================================*/
#include "test_framework.h"
#include "../kernel/include/memory.h"

#include <stdlib.h>

static void *new_arena(size_t size) {
    void *p = malloc(size);          /* malloc is max-aligned: safe for headers */
    heap_init_at(p, size);
    return p;
}

TEST(alloc_is_usable_and_distinct) {
    void *arena = new_arena(8192);
    char *a = (char *)kmalloc(64);
    char *b = (char *)kmalloc(64);
    ASSERT_TRUE(a != NULL && b != NULL);
    ASSERT_TRUE(a != b);
    for (int i = 0; i < 64; i++) { a[i] = (char)i; b[i] = (char)(64 - i); }
    ASSERT_EQ_INT(0, a[0]);
    ASSERT_EQ_INT(63, a[63]);
    free(arena);
}

TEST(free_then_realloc_reclaims) {
    void *arena = new_arena(4096);
    size_t f0 = heap_bytes_free();
    void *a = kmalloc(100);
    ASSERT_TRUE(heap_bytes_free() < f0);
    kfree(a);
    ASSERT_EQ_INT((int)f0, (int)heap_bytes_free());   /* coalesced back to full */
    free(arena);
}

TEST(coalesces_multiple_adjacent_frees) {
    void *arena = new_arena(8192);
    size_t f0 = heap_bytes_free();
    void *a = kmalloc(100);
    void *b = kmalloc(100);
    void *c = kmalloc(100);
    ASSERT_TRUE(a && b && c);
    kfree(a);
    kfree(b);
    kfree(c);
    ASSERT_EQ_INT((int)f0, (int)heap_bytes_free());   /* all three merged back */
    free(arena);
}

TEST(exhaustion_returns_null) {
    void *arena = new_arena(1024);
    void *big = kmalloc(100000);
    ASSERT_TRUE(big == NULL);
    kfree(NULL);                                       /* must be safe */
    free(arena);
}

int main(void) {
    printf("== heap tests ==\n");
    RUN_TEST(alloc_is_usable_and_distinct);
    RUN_TEST(free_then_realloc_reclaims);
    RUN_TEST(coalesces_multiple_adjacent_frees);
    RUN_TEST(exhaustion_returns_null);
    TEST_SUMMARY();
}
