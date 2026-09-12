/*
 * pic.c — 8259A Programmable Interrupt Controller remap.
 *
 * The default PIC IRQ vectors (0x08-0x0F) collide with CPU exception vectors,
 * so we remap the master/slave PICs to 0x20-0x2F before enabling interrupts
 * (TECH-NOTES §3.6). We then unmask only the IRQs we service.
 */
#include "../../include/kernel.h"
#include "io.h"

#define PIC1_CMD   0x20
#define PIC1_DATA  0x21
#define PIC2_CMD   0xA0
#define PIC2_DATA  0xA1
#define PIC_EOI    0x20

#define ICW1_INIT  0x11    /* init + ICW4 present */
#define ICW4_8086  0x01    /* 8086/88 mode */

void pic_remap(uint8_t offset1, uint8_t offset2)
{
    uint8_t mask1 = inb(PIC1_DATA);
    uint8_t mask2 = inb(PIC2_DATA);

    outb(PIC1_CMD, ICW1_INIT); io_wait();
    outb(PIC2_CMD, ICW1_INIT); io_wait();
    outb(PIC1_DATA, offset1);  io_wait();   /* master vector offset */
    outb(PIC2_DATA, offset2);  io_wait();   /* slave  vector offset */
    outb(PIC1_DATA, 4);        io_wait();   /* tell master: slave at IRQ2 */
    outb(PIC2_DATA, 2);        io_wait();   /* tell slave its cascade id */
    outb(PIC1_DATA, ICW4_8086);io_wait();
    outb(PIC2_DATA, ICW4_8086);io_wait();

    outb(PIC1_DATA, mask1);
    outb(PIC2_DATA, mask2);
}

void pic_clear_mask(uint8_t irq)
{
    uint16_t port = irq < 8 ? PIC1_DATA : PIC2_DATA;
    if (irq >= 8) irq -= 8;
    outb(port, inb(port) & ~(1 << irq));
}

void pic_send_eoi(uint8_t irq)
{
    if (irq >= 8) outb(PIC2_CMD, PIC_EOI);
    outb(PIC1_CMD, PIC_EOI);
}

void pic_init(void)
{
    pic_remap(0x20, 0x28);
    /* Mask everything, then unmask the IRQs we actually handle. */
    outb(PIC1_DATA, 0xFF);
    outb(PIC2_DATA, 0xFF);
    pic_clear_mask(0);   /* timer */
    pic_clear_mask(1);   /* keyboard */
    KLOG_DEBUG("pic: remapped to 0x20, timer+keyboard unmasked");
}
