/* =============================================================================
 *  pmm.c  --  Physical frame allocator (bitmap)
 *
 *  One bit per 4 KiB frame. Frame 0 is always reserved (it backs the real-mode
 *  IVT and lets us use physical address 0 as the "no free frame" sentinel).
 *  Sized statically for up to 256 MiB, which is plenty for the QEMU targets in
 *  TECH-NOTES; pmm_init clamps to whatever RAM was reported.
 * ===========================================================================*/
#include "../include/pmm.h"

#define MAX_FRAMES   (256u * 1024u * 1024u / FRAME_SIZE)   /* 65536 frames */
#define BITMAP_WORDS (MAX_FRAMES / 32)

static u32 bitmap[BITMAP_WORDS];
static u32 total_frames = 0;
static u32 used_frames  = 0;

static inline void bit_set(u32 f)   { bitmap[f >> 5] |=  (1u << (f & 31)); }
static inline void bit_clear(u32 f) { bitmap[f >> 5] &= ~(1u << (f & 31)); }
static inline bool bit_test(u32 f)  { return (bitmap[f >> 5] >> (f & 31)) & 1u; }

void pmm_init(u32 mem_size_kb) {
    total_frames = (mem_size_kb / 4);           /* 4 KiB per frame */
    if (total_frames > MAX_FRAMES) total_frames = MAX_FRAMES;
    if (total_frames == 0) total_frames = 1;

    for (u32 i = 0; i < BITMAP_WORDS; i++) bitmap[i] = 0;   /* all free */
    used_frames = 0;

    /* Frames beyond the reported RAM are permanently "used" so we never hand
     * them out, and frame 0 is reserved as the sentinel. */
    for (u32 f = total_frames; f < MAX_FRAMES; f++) bit_set(f);
    bit_set(0);
    used_frames = 1;
}

void pmm_reserve_region(u32 base, u32 length) {
    if (length == 0) return;
    u32 first = base / FRAME_SIZE;                       /* round down */
    u32 last  = (base + length - 1) / FRAME_SIZE;        /* inclusive  */
    for (u32 f = first; f <= last && f < total_frames; f++) {
        if (!bit_test(f)) { bit_set(f); used_frames++; }
    }
}

u32 pmm_alloc_frame(void) {
    for (u32 f = 1; f < total_frames; f++) {            /* skip frame 0 */
        if (!bit_test(f)) {
            bit_set(f);
            used_frames++;
            return f * FRAME_SIZE;
        }
    }
    return 0;                                            /* out of memory */
}

void pmm_free_frame(u32 addr) {
    u32 f = addr / FRAME_SIZE;
    if (f == 0 || f >= total_frames) return;            /* ignore bogus / sentinel */
    if (bit_test(f)) {
        bit_clear(f);
        if (used_frames) used_frames--;
    }
}

u32 pmm_frames_total(void) { return total_frames; }
u32 pmm_frames_free(void)  { return total_frames - used_frames; }
u32 pmm_frames_used(void)  { return used_frames; }
