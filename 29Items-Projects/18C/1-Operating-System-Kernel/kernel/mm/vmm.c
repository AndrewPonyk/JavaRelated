/*
 * vmm.c — Virtual Memory Manager (x86-64 4-level paging).
 *
 * boot.asm already installed a page hierarchy that identity-maps the low 1 GiB
 * and mirrors it into the kernel's higher half. Because the identity map is
 * still present, any page-table frame (always within low physical RAM) can be
 * accessed directly through its physical == identity-virtual address. That lets
 * this allocator walk and edit the live tables without a recursive mapping.
 */
#include "../include/kernel.h"
#include "../include/memory.h"

#define PT_ENTRIES   512
#define ADDR_MASK    0x000FFFFFFFFFF000UL   /* 4 KiB-aligned phys bits of a PTE */
#define PTE_HUGE     (1UL << 7)

struct address_space {
    uint64_t  pml4_phys;
};

static struct address_space kernel_space;

static inline size_t idx4(uintptr_t v) { return (v >> 39) & 0x1FF; }
static inline size_t idx3(uintptr_t v) { return (v >> 30) & 0x1FF; }
static inline size_t idx2(uintptr_t v) { return (v >> 21) & 0x1FF; }
static inline size_t idx1(uintptr_t v) { return (v >> 12) & 0x1FF; }

/* A physical page-table frame is reachable at its identity-mapped address. */
static inline uint64_t *table(uint64_t phys) { return (uint64_t *)phys; }

static inline void invlpg(uintptr_t v)
{
    __asm__ volatile("invlpg (%0)" :: "r"(v) : "memory");
}

static inline uint64_t read_cr3(void)
{
    uint64_t v;
    __asm__ volatile("mov %%cr3, %0" : "=r"(v));
    return v;
}

void vmm_init(void)
{
    kernel_space.pml4_phys = read_cr3() & ADDR_MASK;
    KLOG_DEBUG("vmm: kernel PML4 at phys %x", (unsigned long)kernel_space.pml4_phys);
}

address_space_t *vmm_kernel_space(void) { return &kernel_space; }

/* Return the child table's physical address, allocating it if absent. */
static uint64_t next_level(uint64_t *parent, size_t i, uint64_t flags)
{
    if (!(parent[i] & PTE_PRESENT)) {
        uintptr_t frame = pmm_alloc_frame();
        if (!frame) return 0;
        memset(table(frame), 0, PAGE_SIZE);
        parent[i] = (frame & ADDR_MASK) | PTE_PRESENT | PTE_WRITE | (flags & PTE_USER);
    }
    return parent[i] & ADDR_MASK;
}

int vmm_map(address_space_t *as, uintptr_t virt, uintptr_t phys, uint64_t flags)
{
    if (!as) as = &kernel_space;

    uint64_t pml4 = as->pml4_phys;
    uint64_t pdpt = next_level(table(pml4), idx4(virt), flags);
    if (!pdpt) return -ENOMEM;
    uint64_t pd   = next_level(table(pdpt), idx3(virt), flags);
    if (!pd) return -ENOMEM;
    uint64_t pt   = next_level(table(pd), idx2(virt), flags);
    if (!pt) return -ENOMEM;

    uint64_t *leaf = table(pt);
    if (leaf[idx1(virt)] & PTE_PRESENT) return -EEXIST;
    leaf[idx1(virt)] = (phys & ADDR_MASK) | PTE_PRESENT | flags;
    invlpg(virt);
    return EOK;
}

int vmm_unmap(address_space_t *as, uintptr_t virt)
{
    if (!as) as = &kernel_space;

    uint64_t *pml4 = table(as->pml4_phys);
    if (!(pml4[idx4(virt)] & PTE_PRESENT)) return -ENOENT;
    uint64_t *pdpt = table(pml4[idx4(virt)] & ADDR_MASK);
    if (!(pdpt[idx3(virt)] & PTE_PRESENT)) return -ENOENT;
    uint64_t *pd = table(pdpt[idx3(virt)] & ADDR_MASK);
    if (!(pd[idx2(virt)] & PTE_PRESENT) || (pd[idx2(virt)] & PTE_HUGE)) return -ENOENT;
    uint64_t *pt = table(pd[idx2(virt)] & ADDR_MASK);
    if (!(pt[idx1(virt)] & PTE_PRESENT)) return -ENOENT;

    pt[idx1(virt)] = 0;
    invlpg(virt);
    return EOK;
}

/* Translate a virtual address to physical via the live tables (0 if unmapped).
 * Handles 2 MiB huge pages in the PD level. */
uintptr_t vmm_translate(address_space_t *as, uintptr_t virt)
{
    if (!as) as = &kernel_space;
    uint64_t *pml4 = table(as->pml4_phys);
    if (!(pml4[idx4(virt)] & PTE_PRESENT)) return 0;
    uint64_t *pdpt = table(pml4[idx4(virt)] & ADDR_MASK);
    if (!(pdpt[idx3(virt)] & PTE_PRESENT)) return 0;
    uint64_t *pd = table(pdpt[idx3(virt)] & ADDR_MASK);
    if (!(pd[idx2(virt)] & PTE_PRESENT)) return 0;
    if (pd[idx2(virt)] & PTE_HUGE)
        return (pd[idx2(virt)] & ~0x1FFFFFUL) | (virt & 0x1FFFFF);
    uint64_t *pt = table(pd[idx2(virt)] & ADDR_MASK);
    if (!(pt[idx1(virt)] & PTE_PRESENT)) return 0;
    return (pt[idx1(virt)] & ADDR_MASK) | (virt & 0xFFF);
}

void vmm_switch_space(address_space_t *as)
{
    if (!as || as->pml4_phys == 0) return;
    __asm__ volatile("mov %0, %%cr3" :: "r"(as->pml4_phys) : "memory");
}

address_space_t *vmm_create_space(void)
{
    /* A full implementation would allocate a fresh PML4 and copy the kernel's
     * higher-half entries; kernel-only threads share the kernel space, so this
     * returns it directly. */
    return &kernel_space;
}
