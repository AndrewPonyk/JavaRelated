/* =============================================================================
 *  paging.h  --  32-bit paging (identity map)
 *
 *  Brings up a single page directory whose first entry identity-maps the low
 *  4 MiB (one page table x 1024 x 4 KiB). Everything this kernel uses -- code at
 *  0x1000, stack at 0x90000, heap at 0x100000, VGA at 0xB8000 -- lives in that
 *  window, so enabling paging is transparent: virtual == physical. It's the
 *  foundation the security/isolation story in ARCHITECTURE.md builds on.
 * ===========================================================================*/
#ifndef MINIOS_PAGING_H
#define MINIOS_PAGING_H

#include "types.h"

#define PAGE_SIZE        4096
#define PAGE_PRESENT     0x1
#define PAGE_RW          0x2
#define PAGE_USER        0x4

void paging_init(void);     /* build the identity map and set CR0.PG */
bool paging_enabled(void);

#endif /* MINIOS_PAGING_H */
