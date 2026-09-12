/*
 * serial.c — 16550 UART driver (COM1).
 *
 * Primary log sink for CI: QEMU forwards COM1 to stdio, so the test harness
 * greps this stream for the boot banner and for PANIC/ERROR markers.
 */
#include "../include/kernel.h"

#define COM1 0x3F8

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

void serial_init(void)
{
    outb(COM1 + 1, 0x00);   /* disable interrupts        */
    outb(COM1 + 3, 0x80);   /* enable DLAB               */
    outb(COM1 + 0, 0x03);   /* divisor low  (38400 baud) */
    outb(COM1 + 1, 0x00);   /* divisor high              */
    outb(COM1 + 3, 0x03);   /* 8 bits, no parity, 1 stop */
    outb(COM1 + 2, 0xC7);   /* enable + clear FIFO       */
    outb(COM1 + 4, 0x0B);   /* IRQs enabled, RTS/DSR set */
}

static int tx_ready(void) { return inb(COM1 + 5) & 0x20; }

void serial_putc(char c)
{
    while (!tx_ready()) { /* spin */ }
    outb(COM1, (uint8_t)c);
}
