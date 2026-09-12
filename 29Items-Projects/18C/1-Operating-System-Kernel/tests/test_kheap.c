/*
 * test_kheap.c — Host unit tests for the kernel heap allocator.
 *
 * Compiles kheap.c against a static heap buffer and host stubs for the kernel
 * logging it references. Covers allocation, coalescing reuse, kcalloc zeroing,
 * the integer-overflow guard, NULL-free safety, and an alloc/free stress loop.
 */
#include "test_framework.h"
#include "../kernel/include/memory.h"

/* Host stubs for kernel logging used inside kheap.c. */
int  kprintf(const char *fmt, ...) { (void)fmt; return 0; }
void klog(int level, const char *fmt, ...) { (void)level; (void)fmt; }

static unsigned char heap[256 * 1024] __attribute__((aligned(16)));

static void reset(void) { kheap_init((uintptr_t)heap, sizeof(heap)); }

TEST(alloc_returns_distinct_nonnull)
{
    reset();
    void *a = kmalloc(64);
    void *b = kmalloc(64);
    ASSERT_NOT_NULL(a);
    ASSERT_NOT_NULL(b);
    ASSERT_NE(a, b);
}

TEST(memory_holds_data)
{
    reset();
    unsigned char *p = kmalloc(128);
    ASSERT_NOT_NULL(p);
    for (int i = 0; i < 128; i++) p[i] = (unsigned char)(i * 7);
    int ok = 1;
    for (int i = 0; i < 128; i++) if (p[i] != (unsigned char)(i * 7)) ok = 0;
    ASSERT_TRUE(ok);
}

TEST(free_then_realloc_reuses_block)
{
    reset();
    void *a = kmalloc(1000);
    kfree(a);
    void *b = kmalloc(1000);
    ASSERT_EQ(a, b);              /* coalesced free block is reused (first-fit) */
}

TEST(kcalloc_zeroes_memory)
{
    reset();
    unsigned char *p = kcalloc(100, 1);
    ASSERT_NOT_NULL(p);
    int zero = 1;
    for (int i = 0; i < 100; i++) if (p[i] != 0) zero = 0;
    ASSERT_TRUE(zero);
}

TEST(kcalloc_overflow_returns_null)
{
    reset();
    /* n * size overflows size_t -> must fail rather than under-allocate. */
    ASSERT_NULL(kcalloc((size_t)-1, 2));
}

TEST(free_null_is_safe)
{
    reset();
    kfree(NULL);                 /* must be a no-op, not a crash */
    ASSERT_NOT_NULL(kmalloc(16));
}

TEST(oversized_alloc_fails_gracefully)
{
    reset();
    ASSERT_NULL(kmalloc(sizeof(heap) * 2));   /* larger than the heap window */
}

TEST(stress_alloc_free_no_corruption)
{
    reset();
    for (int round = 0; round < 2000; round++) {
        void *p = kmalloc(64);
        ASSERT_NOT_NULL(p);
        kfree(p);
    }
    /* After many cycles a large allocation still succeeds (no leak/corruption). */
    ASSERT_NOT_NULL(kmalloc(128 * 1024));
}

int main(void)
{
    printf("kheap tests:\n");
    RUN(alloc_returns_distinct_nonnull);
    RUN(memory_holds_data);
    RUN(free_then_realloc_reuses_block);
    RUN(kcalloc_zeroes_memory);
    RUN(kcalloc_overflow_returns_null);
    RUN(free_null_is_safe);
    RUN(oversized_alloc_fails_gracefully);
    RUN(stress_alloc_free_no_corruption);
    return test_summary();
}
