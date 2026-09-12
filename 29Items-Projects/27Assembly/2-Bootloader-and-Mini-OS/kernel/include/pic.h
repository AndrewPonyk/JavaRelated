/* =============================================================================
 *  pic.h  --  8259 Programmable Interrupt Controller
 *
 *  The two cascaded 8259 PICs default to raising IRQs on vectors 0x08-0x0F,
 *  which collide with CPU exception vectors. We remap them to 0x20-0x2F so
 *  hardware IRQs and CPU faults never share a vector (see TECH-NOTES pitfalls).
 * ===========================================================================*/
#ifndef MINIOS_PIC_H
#define MINIOS_PIC_H

#include "types.h"

#define PIC1_CMD    0x20
#define PIC1_DATA   0x21
#define PIC2_CMD    0xA0
#define PIC2_DATA   0xA1
#define PIC_EOI     0x20        /* "End Of Interrupt" command byte */

#define PIC1_OFFSET 0x20        /* master PIC -> vectors 0x20-0x27 */
#define PIC2_OFFSET 0x28        /* slave  PIC -> vectors 0x28-0x2F */

void pic_remap(void);           /* relocate IRQs to 0x20-0x2F */
void pic_send_eoi(u8 irq);      /* acknowledge an IRQ so the line can fire again */
void pic_set_mask(u8 irq_line); /* disable a single IRQ line */
void pic_clear_mask(u8 irq_line);/* enable a single IRQ line */

#endif /* MINIOS_PIC_H */
