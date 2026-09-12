/*
 * test_pmm.c — Host unit tests for the physical frame allocator.
 *
 * Compiles pmm.c against host stubs for kprintf/klog. Validates allocation,
 * freeing, the free-count accounting, and double-free safety.
 */
#include "test_framework.h"
#include "../kernel/include/memory.h"

/* Host stubs for the kernel logging used inside pmm.c */
int kprintf(const char *fmt, ...) { (void)fmt; return 0; }
void klog(int level, const char *fmt, ...) { (void)level; (void)fmt; }

#define TEST_BASE  0x100000UL
#define TEST_LEN   (1024UL * PAGE_SIZE)   /* 1024 frames */

TEST(init_reports_all_free)
{
    pmm_init(TEST_BASE, TEST_LEN);
    ASSERT_EQ(pmm_free_count(), 1024UL);
}

TEST(alloc_decrements_free_count)
{
    pmm_init(TEST_BASE, TEST_LEN);
    uintptr_t a = pmm_alloc_frame();
    ASSERT_NE(a, 0UL);
    ASSERT_EQ(pmm_free_count(), 1023UL);
    ASSERT_TRUE((a % PAGE_SIZE) == 0);        /* page-aligned */
}

TEST(free_returns_frame_to_pool)
{
    pmm_init(TEST_BASE, TEST_LEN);
    uintptr_t a = pmm_alloc_frame();
    pmm_free_frame(a);
    ASSERT_EQ(pmm_free_count(), 1024UL);
}

TEST(distinct_frames_are_unique)
{
    pmm_init(TEST_BASE, TEST_LEN);
    uintptr_t a = pmm_alloc_frame();
    uintptr_t b = pmm_alloc_frame();
    ASSERT_NE(a, b);
}

TEST(double_free_is_ignored)
{
    pmm_init(TEST_BASE, TEST_LEN);
    uintptr_t a = pmm_alloc_frame();
    pmm_free_frame(a);
    pmm_free_frame(a);                        /* must not corrupt the count */
    ASSERT_EQ(pmm_free_count(), 1024UL);
}

int main(void)
{
    printf("pmm tests:\n");
    RUN(init_reports_all_free);
    RUN(alloc_decrements_free_count);
    RUN(free_returns_frame_to_pool);
    RUN(distinct_frames_are_unique);
    RUN(double_free_is_ignored);
    return test_summary();
}
