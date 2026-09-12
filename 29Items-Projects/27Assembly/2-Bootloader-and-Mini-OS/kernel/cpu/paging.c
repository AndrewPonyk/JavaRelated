/* =============================================================================
 *  paging.c  --  Identity-mapped 32-bit paging
 *
 *  Page directory + one page table, both 4 KiB-aligned (a hard CPU requirement;
 *  the low 12 bits of each table-base hold flags). The single present directory
 *  entry maps virtual [0, 4 MiB) straight onto the same physical range.
 * ===========================================================================*/
#include "../include/paging.h"

/* Must be page-aligned; the static arrays live in .bss within the low 4 MiB. */
static u32 page_directory[1024] __attribute__((aligned(PAGE_SIZE)));
static u32 first_page_table[1024] __attribute__((aligned(PAGE_SIZE)));
static bool enabled = false;

static void load_page_directory(u32 phys) {
    __asm__ volatile ("mov %0, %%cr3" : : "r"(phys));
}

static void enable_paging_bit(void) {
    u32 cr0;
    __asm__ volatile ("mov %%cr0, %0" : "=r"(cr0));
    cr0 |= 0x80000000;                      /* CR0.PG */
    __asm__ volatile ("mov %0, %%cr0" : : "r"(cr0));
}

void paging_init(void) {
    /* Identity-map the first 4 MiB: page i -> physical i*4096. */
    for (u32 i = 0; i < 1024; i++) {
        first_page_table[i] = (i * PAGE_SIZE) | PAGE_PRESENT | PAGE_RW;
    }

    /* Directory entry 0 points at that table; the rest are not present. */
    page_directory[0] = ((u32)(uintptr_t)first_page_table) | PAGE_PRESENT | PAGE_RW;
    for (u32 i = 1; i < 1024; i++) {
        page_directory[i] = PAGE_RW;        /* writable but not present */
    }

    load_page_directory((u32)(uintptr_t)page_directory);
    enable_paging_bit();
    enabled = true;
}

bool paging_enabled(void) { return enabled; }
