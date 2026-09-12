/* =============================================================================
 *  idt.h  --  Interrupt Descriptor Table
 *
 *  The IDT maps each of the 256 interrupt vectors to a handler (an "interrupt
 *  gate"). This is the kernel's API gateway: every fault, IRQ, and future
 *  syscall enters through here.
 * ===========================================================================*/
#ifndef MINIOS_IDT_H
#define MINIOS_IDT_H

#include "types.h"

#define IDT_ENTRIES 256
#define KERNEL_CS   0x08        /* matches CODE_SEG in boot/gdt.asm */

/* A single 64-bit IDT gate descriptor. Hardware-defined bit layout, so it MUST
 * be packed -- any compiler padding here corrupts the table. */
typedef struct {
    u16 base_low;               /* handler address bits 0-15  */
    u16 selector;               /* code segment selector       */
    u8  always0;                /* reserved, must be zero      */
    u8  flags;                  /* P | DPL | gate type         */
    u16 base_high;              /* handler address bits 16-31  */
} __attribute__((packed)) idt_entry_t;

/* The IDTR register payload handed to the `lidt` instruction. */
typedef struct {
    u16 limit;                  /* size of the IDT in bytes - 1 */
    u32 base;                   /* linear address of the table  */
} __attribute__((packed)) idt_ptr_t;

void idt_set_gate(u8 num, u32 handler, u16 selector, u8 flags);
void idt_install(void);         /* build the table and execute `lidt` */

/* Defined in cpu/idt_load.asm: loads the IDTR from the given pointer. */
extern void idt_load(idt_ptr_t *idt_ptr);

#endif /* MINIOS_IDT_H */
