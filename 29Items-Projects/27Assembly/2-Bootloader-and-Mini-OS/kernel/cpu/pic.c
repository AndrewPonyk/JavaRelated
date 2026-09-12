/* =============================================================================
 *  pic.c  --  8259 Programmable Interrupt Controller
 *
 *  Remaps the master/slave PICs away from their default 0x08-0x0F vectors
 *  (which clash with CPU exceptions) to 0x20-0x2F, and provides EOI + masking.
 * ===========================================================================*/
#include "../include/pic.h"
#include "../include/ports.h"

#define ICW1_INIT   0x10
#define ICW1_ICW4   0x01
#define ICW4_8086   0x01

void pic_remap(void) {
    u8 mask1 = inb(PIC1_DATA);          /* preserve currently-masked lines */
    u8 mask2 = inb(PIC2_DATA);

    /* ICW1: begin initialization (cascade mode, expect ICW4). */
    outb(PIC1_CMD, ICW1_INIT | ICW1_ICW4); io_wait();
    outb(PIC2_CMD, ICW1_INIT | ICW1_ICW4); io_wait();

    /* ICW2: vector offsets. */
    outb(PIC1_DATA, PIC1_OFFSET); io_wait();    /* master -> 0x20 */
    outb(PIC2_DATA, PIC2_OFFSET); io_wait();    /* slave  -> 0x28 */

    /* ICW3: wire master/slave cascade on IRQ2. */
    outb(PIC1_DATA, 0x04); io_wait();           /* slave is at IRQ2 (bit 2) */
    outb(PIC2_DATA, 0x02); io_wait();           /* slave cascade identity   */

    /* ICW4: 8086/88 mode. */
    outb(PIC1_DATA, ICW4_8086); io_wait();
    outb(PIC2_DATA, ICW4_8086); io_wait();

    /* Restore saved masks. */
    outb(PIC1_DATA, mask1);
    outb(PIC2_DATA, mask2);
}

void pic_send_eoi(u8 irq) {
    if (irq >= 8) outb(PIC2_CMD, PIC_EOI);  /* slave first, if applicable */
    outb(PIC1_CMD, PIC_EOI);                /* always ack the master */
}

void pic_set_mask(u8 irq_line) {
    u16 port = (irq_line < 8) ? PIC1_DATA : PIC2_DATA;
    if (irq_line >= 8) irq_line -= 8;
    outb(port, (u8)(inb(port) | (1 << irq_line)));
}

void pic_clear_mask(u8 irq_line) {
    u16 port = (irq_line < 8) ? PIC1_DATA : PIC2_DATA;
    if (irq_line >= 8) irq_line -= 8;
    outb(port, (u8)(inb(port) & ~(1 << irq_line)));
}
