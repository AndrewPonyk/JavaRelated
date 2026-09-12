/*
 * gdt.c — Global Descriptor Table status.
 *
 * The active GDT is the flat 64-bit table established in boot.asm (null +
 * kernel code + kernel data) before the jump to long mode; it is sufficient for
 * the kernel-mode operation this OS performs. This module exposes the selectors
 * and a verification hook so the descriptor setup is observable from C.
 *
 * (A per-CPU TSS with separate user/kernel descriptors is what a ring-3
 * userspace would add here; see README "Scope".)
 */
#include "../../include/kernel.h"

#define SEL_KERNEL_CODE 0x08
#define SEL_KERNEL_DATA 0x10

/* Read the current code selector to confirm we are on the expected descriptor. */
static inline uint16_t read_cs(void)
{
    uint16_t cs;
    __asm__ volatile("mov %%cs, %0" : "=r"(cs));
    return cs;
}

void gdt_init(void)
{
    uint16_t cs = read_cs();
    if (cs != SEL_KERNEL_CODE)
        panic("gdt: unexpected CS=%x (want %x)", cs, SEL_KERNEL_CODE);
    KLOG_DEBUG("gdt: boot GDT active, CS=%x DS=kernel-data", cs);
}
