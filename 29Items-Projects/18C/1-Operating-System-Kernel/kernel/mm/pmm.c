/*
 * pmm.c — Physical Memory Manager (bitmap frame allocator).
 *
 * One bit per 4KiB frame: 1 = used, 0 = free. Simple and easy to reason about;
 * see ARCHITECTURE.md §2.4 for the planned buddy/slab upgrade path.
 */
#include "../include/kernel.h"
#include "../include/memory.h"

#define FRAMES_PER_WORD  64
#define MAX_FRAMES       (1UL << 20)   /* covers 4 GiB of RAM */

static uint64_t  bitmap[MAX_FRAMES / FRAMES_PER_WORD];
static size_t    total_frames;
static size_t    free_frames;
static uintptr_t base_addr;

static inline void set_used(size_t idx) { bitmap[idx / 64] |=  (1UL << (idx % 64)); }
static inline void set_free(size_t idx) { bitmap[idx / 64] &= ~(1UL << (idx % 64)); }
static inline bool is_used(size_t idx)  { return bitmap[idx / 64] & (1UL << (idx % 64)); }

void pmm_init(uintptr_t mem_base, size_t mem_len)
{
    base_addr    = mem_base;
    total_frames = mem_len / PAGE_SIZE;
    if (total_frames > MAX_FRAMES) total_frames = MAX_FRAMES;

    /* Mark everything used, then free the usable region. Conservative default
     * keeps us from handing out frames the bootloader still owns. */
    for (size_t i = 0; i < sizeof(bitmap) / sizeof(bitmap[0]); i++)
        bitmap[i] = ~0UL;

    free_frames = 0;
    for (size_t i = 0; i < total_frames; i++) {
        set_free(i);
        free_frames++;
    }
    KLOG_DEBUG("pmm: %lu frames over %lu MiB",
               (unsigned long)total_frames, (unsigned long)(mem_len >> 20));
}

uintptr_t pmm_alloc_frame(void)
{
    /* Linear first-fit scan. A search cursor would avoid rescanning from 0; the
     * bitmap is intentionally simple (see ARCHITECTURE.md §2.4). */
    for (size_t i = 0; i < total_frames; i++) {
        if (!is_used(i)) {
            set_used(i);
            free_frames--;
            return base_addr + (uintptr_t)i * PAGE_SIZE;
        }
    }
    KLOG_ERROR("pmm: out of physical memory");
    return 0; /* OOM */
}

void pmm_free_frame(uintptr_t frame)
{
    if (frame < base_addr) {
        KLOG_WARN("pmm: free of out-of-range frame %p", (void *)frame);
        return;
    }
    size_t idx = (frame - base_addr) / PAGE_SIZE;
    if (idx >= total_frames || !is_used(idx)) {
        KLOG_WARN("pmm: double/invalid free at idx %lu", (unsigned long)idx);
        return;
    }
    set_free(idx);
    free_frames++;
}

size_t pmm_free_count(void) { return free_frames; }
