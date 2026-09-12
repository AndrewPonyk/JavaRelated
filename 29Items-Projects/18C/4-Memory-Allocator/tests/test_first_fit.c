/**
 * @file test_first_fit.c
 * @brief Unit tests for first-fit allocation, reuse, and alignment.
 */
#include "test_framework.h"
#include "memalloc.h"

#include <stdint.h>
#include <string.h>

static int test_basic_alloc(void) {
    ASSERT_EQ(mem_init(MEM_FIRST_FIT), 0);
    void *a = mem_malloc(64);
    ASSERT_NOT_NULL(a);
    memset(a, 0xAB, 64); /* writable for its whole length */
    mem_free(a);
    mem_destroy();
    return 0;
}

static int test_zero_size_returns_null(void) {
    ASSERT_EQ(mem_init(MEM_FIRST_FIT), 0);
    ASSERT_NULL(mem_malloc(0));
    mem_destroy();
    return 0;
}

static int test_alignment(void) {
    ASSERT_EQ(mem_init(MEM_FIRST_FIT), 0);
    for (size_t s = 1; s <= 256; s <<= 1) {
        void *p = mem_malloc(s);
        ASSERT_NOT_NULL(p);
        ASSERT_EQ((uintptr_t)p % 16u, 0u); /* 16-byte aligned payloads */
    }
    mem_destroy();
    return 0;
}

static int test_reuse_after_free(void) {
    ASSERT_EQ(mem_init(MEM_FIRST_FIT), 0);
    void *a = mem_malloc(128);
    ASSERT_NOT_NULL(a);
    mem_free(a);
    void *b = mem_malloc(64); /* should reuse the freed 128B region */
    ASSERT_NOT_NULL(b);
    mem_free(b);
    mem_destroy();
    return 0;
}

static int test_calloc_zeroes(void) {
    ASSERT_EQ(mem_init(MEM_FIRST_FIT), 0);
    unsigned char *p = mem_calloc(16, 4);
    ASSERT_NOT_NULL(p);
    for (int i = 0; i < 64; i++) {
        ASSERT_EQ(p[i], 0);
    }
    mem_free(p);
    mem_destroy();
    return 0;
}

int main(void) {
    RUN_TEST(test_basic_alloc);
    RUN_TEST(test_zero_size_returns_null);
    RUN_TEST(test_alignment);
    RUN_TEST(test_reuse_after_free);
    RUN_TEST(test_calloc_zeroes);
    return TEST_SUMMARY();
}
