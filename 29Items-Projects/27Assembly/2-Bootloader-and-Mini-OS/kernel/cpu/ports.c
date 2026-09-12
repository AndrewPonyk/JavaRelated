/* =============================================================================
 *  ports.c  --  Port-mapped I/O primitives (inline assembly)
 * ===========================================================================*/
#include "../include/ports.h"

u8 inb(u16 port) {
    u8 result;
    __asm__ volatile ("inb %1, %0" : "=a"(result) : "Nd"(port));
    return result;
}

void outb(u16 port, u8 value) {
    __asm__ volatile ("outb %0, %1" : : "a"(value), "Nd"(port));
}

u16 inw(u16 port) {
    u16 result;
    __asm__ volatile ("inw %1, %0" : "=a"(result) : "Nd"(port));
    return result;
}

void outw(u16 port, u16 value) {
    __asm__ volatile ("outw %0, %1" : : "a"(value), "Nd"(port));
}

void io_wait(void) {
    /* Write to unused port 0x80; the bus cycle gives slow hardware time. */
    __asm__ volatile ("outb %%al, $0x80" : : "a"((u8)0));
}
