/* =============================================================================
 *  memory.h  --  Kernel memory management
 *
 *  Two layers:
 *    - mem* helpers: freestanding memcpy/memset/memmove (no libc available).
 *    - heap: a simple kmalloc/kfree backed by a free-list over a fixed region
 *      starting at KHEAP_START. The physical memory manager (pmm.c) will later
 *      hand pages to this heap instead of a hard-coded region.
 * ===========================================================================*/
#ifndef MINIOS_MEMORY_H
#define MINIOS_MEMORY_H

#include "types.h"

#define KHEAP_START 0x100000    /* 1 MiB: start of extended memory */
#define KHEAP_SIZE  0x100000    /* 1 MiB heap (grow with the PMM later) */

/* --- raw memory helpers (also unit-tested on the host, see tests/) --- */
void *memset(void *dest, int value, size_t count);
void *memcpy(void *dest, const void *src, size_t count);
void *memmove(void *dest, const void *src, size_t count);

/* --- kernel heap --- */
void  heap_init(void);                       /* heap over [KHEAP_START, +SIZE) */
void  heap_init_at(void *base, size_t size); /* heap over an arbitrary region   */
void *kmalloc(size_t size);     /* returns NULL on exhaustion (recoverable) */
void  kfree(void *ptr);

/* Diagnostics for tests / panic screens. */
size_t heap_bytes_free(void);

#endif /* MINIOS_MEMORY_H */
