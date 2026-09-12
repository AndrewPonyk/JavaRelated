/*
 * memory.h — Memory management interfaces: PMM, VMM, kernel heap.
 */
#ifndef KERNEL_MEMORY_H
#define KERNEL_MEMORY_H

#include "types.h"

/* Page size lives here so the allocator (and its unit tests, which include only
 * this header) are self-contained. kernel.h defines the same value, guarded. */
#ifndef PAGE_SIZE
#define PAGE_SIZE 4096UL
#endif

/* ---- Physical Memory Manager (frame allocator) ------------------ */
/* Initialize the bitmap allocator over [base, base+len). */
void      pmm_init(uintptr_t mem_base, size_t mem_len);
/* Allocate / free a single 4KiB physical frame. Returns 0 on OOM. */
uintptr_t pmm_alloc_frame(void);
void      pmm_free_frame(uintptr_t frame);
size_t    pmm_free_count(void); /* frames still available */

/* ---- Virtual Memory Manager (4-level paging) -------------------- */
#define PTE_PRESENT  (1UL << 0)
#define PTE_WRITE    (1UL << 1)
#define PTE_USER     (1UL << 2)
#define PTE_NX       (1UL << 63)

typedef struct address_space address_space_t; /* opaque (PML4 wrapper) */

void  vmm_init(void);
int   vmm_map(address_space_t *as, uintptr_t virt, uintptr_t phys, uint64_t flags);
int   vmm_unmap(address_space_t *as, uintptr_t virt);
uintptr_t vmm_translate(address_space_t *as, uintptr_t virt);
void  vmm_switch_space(address_space_t *as);          /* load CR3 */
address_space_t *vmm_create_space(void);              /* new PML4 */
address_space_t *vmm_kernel_space(void);

/* ---- Kernel Heap ------------------------------------------------ */
void  kheap_init(uintptr_t start, size_t size);
void *kmalloc(size_t size);
void *kcalloc(size_t n, size_t size);
void  kfree(void *ptr);

#endif /* KERNEL_MEMORY_H */
