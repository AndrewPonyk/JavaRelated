/* =============================================================================
 *  pmm.h  --  Physical Memory Manager (4 KiB frame bitmap allocator)
 *
 *  Tracks physical RAM one bit per 4 KiB frame (1 = used, 0 = free). The kernel
 *  initializes it with the total memory size, reserves the regions that are
 *  already spoken for (low 1 MiB + kernel + heap), and then hands out frames
 *  from what remains. Pure (no hardware deps) so the bitmap logic is host-tested.
 * ===========================================================================*/
#ifndef MINIOS_PMM_H
#define MINIOS_PMM_H

#include "types.h"

#define FRAME_SIZE 4096

void pmm_init(u32 mem_size_kb);                 /* all frames start FREE */
void pmm_reserve_region(u32 base, u32 length);  /* mark [base,base+len) used */

u32  pmm_alloc_frame(void);     /* physical base addr of a free frame, or 0 */
void pmm_free_frame(u32 addr);

u32  pmm_frames_total(void);
u32  pmm_frames_free(void);
u32  pmm_frames_used(void);

#endif /* MINIOS_PMM_H */
