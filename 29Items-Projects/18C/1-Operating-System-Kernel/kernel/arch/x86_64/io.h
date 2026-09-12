/*
 * io.h — x86 port I/O helpers (shared by drivers and arch code).
 */
#ifndef ARCH_X86_64_IO_H
#define ARCH_X86_64_IO_H

#include "../../include/types.h"

static inline void outb(uint16_t port, uint8_t val)
{
    __asm__ volatile("outb %0, %1" :: "a"(val), "Nd"(port));
}

static inline uint8_t inb(uint16_t port)
{
    uint8_t r;
    __asm__ volatile("inb %1, %0" : "=a"(r) : "Nd"(port));
    return r;
}

static inline void outw(uint16_t port, uint16_t val)
{
    __asm__ volatile("outw %0, %1" :: "a"(val), "Nd"(port));
}

static inline void outl(uint16_t port, uint32_t val)
{
    __asm__ volatile("outl %0, %1" :: "a"(val), "Nd"(port));
}

/* Short delay by writing to an unused port (lets the PIC settle). */
static inline void io_wait(void)
{
    outb(0x80, 0);
}

#endif /* ARCH_X86_64_IO_H */
