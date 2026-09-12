/* =============================================================================
 *  test_pmm.c  --  Host unit tests for the physical frame allocator
 *
 *  Compiled with kernel/mm/pmm.c (pure, no hardware deps). Verifies init sizing,
 *  region reservation, allocation order, exhaustion, and free/double-free.
 * ===========================================================================*/
#include "test_framework.h"
#include "../kernel/include/pmm.h"

TEST(init_sizes_bitmap_and_reserves_frame0) {
    pmm_init(16 * 1024);                    /* 16 MiB -> 4096 frames */
    ASSERT_EQ_INT(4096, pmm_frames_total());
    ASSERT_EQ_INT(1, pmm_frames_used());    /* frame 0 reserved as sentinel */
    ASSERT_EQ_INT(4095, pmm_frames_free());
}

TEST(alloc_returns_first_free_then_frees) {
    pmm_init(16 * 1024);
    u32 a = pmm_alloc_frame();
    ASSERT_EQ_INT(0x1000, a);               /* frame 1 = 4 KiB */
    ASSERT_EQ_INT(4094, pmm_frames_free());
    pmm_free_frame(a);
    ASSERT_EQ_INT(4095, pmm_frames_free());
}

TEST(reserve_region_blocks_allocation) {
    pmm_init(16 * 1024);
    pmm_reserve_region(0, 0x100000);        /* reserve low 1 MiB = 256 frames */
    ASSERT_EQ_INT(4096 - 256, pmm_frames_free());
    u32 a = pmm_alloc_frame();
    ASSERT_EQ_INT(0x100000, a);             /* first free frame is now at 1 MiB */
}

TEST(allocation_exhausts_cleanly) {
    pmm_init(64);                           /* 64 KiB -> 16 frames, 15 usable */
    int count = 0;
    while (pmm_alloc_frame() != 0) count++;
    ASSERT_EQ_INT(15, count);
    ASSERT_EQ_INT(0, pmm_frames_free());
    ASSERT_EQ_INT(0, pmm_alloc_frame());    /* further allocs return 0 */
}

TEST(double_free_and_bogus_free_are_safe) {
    pmm_init(64);
    u32 a = pmm_alloc_frame();
    pmm_free_frame(a);
    u32 free_after = pmm_frames_free();
    pmm_free_frame(a);                       /* double free: no-op */
    pmm_free_frame(0);                        /* frame 0 / sentinel: ignored */
    pmm_free_frame(0xDEAD0000);               /* out of range: ignored */
    ASSERT_EQ_INT((int)free_after, (int)pmm_frames_free());
}

int main(void) {
    printf("== pmm tests ==\n");
    RUN_TEST(init_sizes_bitmap_and_reserves_frame0);
    RUN_TEST(alloc_returns_first_free_then_frees);
    RUN_TEST(reserve_region_blocks_allocation);
    RUN_TEST(allocation_exhausts_cleanly);
    RUN_TEST(double_free_and_bogus_free_are_safe);
    TEST_SUMMARY();
}
