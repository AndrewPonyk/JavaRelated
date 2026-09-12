/* =============================================================================
 *  idt.c  --  Build and load the Interrupt Descriptor Table
 * ===========================================================================*/
#include "../include/idt.h"
#include "../include/memory.h"   /* memset */

static idt_entry_t idt[IDT_ENTRIES];
static idt_ptr_t   idt_ptr;

void idt_set_gate(u8 num, u32 handler, u16 selector, u8 flags) {
    idt[num].base_low  = (u16)(handler & 0xFFFF);
    idt[num].base_high = (u16)((handler >> 16) & 0xFFFF);
    idt[num].selector  = selector;
    idt[num].always0   = 0;
    idt[num].flags     = flags;     /* e.g. 0x8E = present, ring 0, 32-bit gate */
}

void idt_install(void) {
    idt_ptr.limit = (u16)(sizeof(idt_entry_t) * IDT_ENTRIES - 1);
    idt_ptr.base  = (u32)&idt;

    memset(&idt, 0, sizeof(idt));   /* zero every gate; isr.c fills them in */

    idt_load(&idt_ptr);             /* execute `lidt` (cpu/idt_load.asm) */
}
