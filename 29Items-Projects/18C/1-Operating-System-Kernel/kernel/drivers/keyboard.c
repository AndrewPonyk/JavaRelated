/*
 * keyboard.c — PS/2 keyboard driver (IRQ1).
 *
 * Reads scancodes from port 0x60 on each IRQ, translates via a US layout, and
 * pushes characters into a ring buffer that the shell's read() drains.
 */
#include "../include/kernel.h"

#define KBD_DATA   0x60
#define RING_SIZE  128

static char    ring[RING_SIZE];
static volatile size_t head, tail;     /* volatile: shared with IRQ context */

/* Minimal US scancode -> ASCII (set 1), unshifted. 0 = unmapped. */
static const char scancode_map[128] = {
    0,  27, '1','2','3','4','5','6','7','8','9','0','-','=','\b',
    '\t','q','w','e','r','t','y','u','i','o','p','[',']','\n',
    0,  'a','s','d','f','g','h','j','k','l',';','\'','`',
    0,  '\\','z','x','c','v','b','n','m',',','.','/', 0,
    '*', 0, ' ',
    /* remainder unmapped */
};

static inline uint8_t inb(uint16_t port)
{
    uint8_t r;
    __asm__ volatile("inb %1, %0" : "=a"(r) : "Nd"(port));
    return r;
}

void keyboard_init(void)
{
    head = tail = 0;
    /* IRQ1 is unmasked in pic_init(); idt.c routes vector 33 to keyboard_isr. */
    KLOG_DEBUG("keyboard: PS/2 driver ready");
}

/* Called from the IRQ1 handler. */
void keyboard_isr(void)
{
    uint8_t sc = inb(KBD_DATA);
    if (sc & 0x80) return;                 /* key release: ignore */
    char c = scancode_map[sc & 0x7F];
    if (!c) return;

    size_t next = (head + 1) % RING_SIZE;
    if (next != tail) {                    /* drop on overflow */
        ring[head] = c;
        head = next;
    }
}

/* Non-blocking read of one buffered char; returns -1 when empty. */
int keyboard_getc(void)
{
    if (tail == head) return -1;
    char c = ring[tail];
    tail = (tail + 1) % RING_SIZE;
    return (unsigned char)c;
}
