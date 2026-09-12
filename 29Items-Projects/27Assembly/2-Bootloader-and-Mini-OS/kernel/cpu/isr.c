/* =============================================================================
 *  isr.c  --  C side of interrupt handling
 *
 *  Installs all 48 IDT gates (32 CPU exceptions + 16 IRQs), dispatches CPU
 *  faults to a panic path, and routes hardware IRQs to registered device
 *  callbacks -- always sending the PIC an End-Of-Interrupt afterward.
 * ===========================================================================*/
#include "../include/isr.h"
#include "../include/idt.h"
#include "../include/pic.h"
#include "../include/vga.h"
#include "../include/scheduler.h"
#include "../include/kprintf.h"

/* Assembly stubs from isr_stubs.asm. */
extern void isr0(void);  extern void isr1(void);  extern void isr2(void);
extern void isr3(void);  extern void isr4(void);  extern void isr5(void);
extern void isr6(void);  extern void isr7(void);  extern void isr8(void);
extern void isr9(void);  extern void isr10(void); extern void isr11(void);
extern void isr12(void); extern void isr13(void); extern void isr14(void);
extern void isr15(void); extern void isr16(void); extern void isr17(void);
extern void isr18(void); extern void isr19(void); extern void isr20(void);
extern void isr21(void); extern void isr22(void); extern void isr23(void);
extern void isr24(void); extern void isr25(void); extern void isr26(void);
extern void isr27(void); extern void isr28(void); extern void isr29(void);
extern void isr30(void); extern void isr31(void);
extern void irq0(void);  extern void irq1(void);  extern void irq2(void);
extern void irq3(void);  extern void irq4(void);  extern void irq5(void);
extern void irq6(void);  extern void irq7(void);  extern void irq8(void);
extern void irq9(void);  extern void irq10(void); extern void irq11(void);
extern void irq12(void); extern void irq13(void); extern void irq14(void);
extern void irq15(void);

static isr_handler_t interrupt_handlers[IDT_ENTRIES] = {0};

static const char *exception_messages[32] = {
    "Division By Zero", "Debug", "Non Maskable Interrupt", "Breakpoint",
    "Into Detected Overflow", "Out of Bounds", "Invalid Opcode",
    "No Coprocessor", "Double Fault", "Coprocessor Segment Overrun",
    "Bad TSS", "Segment Not Present", "Stack Fault",
    "General Protection Fault", "Page Fault", "Unknown Interrupt",
    "Coprocessor Fault", "Alignment Check", "Machine Check",
    "Reserved","Reserved","Reserved","Reserved","Reserved","Reserved",
    "Reserved","Reserved","Reserved","Reserved","Reserved","Reserved","Reserved"
};

#define GATE_FLAGS 0x8E         /* present, ring 0, 32-bit interrupt gate */

void isr_install(void) {
    idt_install();              /* zero the table + lidt */

    idt_set_gate(0,  (u32)isr0,  KERNEL_CS, GATE_FLAGS);
    idt_set_gate(1,  (u32)isr1,  KERNEL_CS, GATE_FLAGS);
    idt_set_gate(2,  (u32)isr2,  KERNEL_CS, GATE_FLAGS);
    idt_set_gate(3,  (u32)isr3,  KERNEL_CS, GATE_FLAGS);
    idt_set_gate(4,  (u32)isr4,  KERNEL_CS, GATE_FLAGS);
    idt_set_gate(5,  (u32)isr5,  KERNEL_CS, GATE_FLAGS);
    idt_set_gate(6,  (u32)isr6,  KERNEL_CS, GATE_FLAGS);
    idt_set_gate(7,  (u32)isr7,  KERNEL_CS, GATE_FLAGS);
    idt_set_gate(8,  (u32)isr8,  KERNEL_CS, GATE_FLAGS);
    idt_set_gate(9,  (u32)isr9,  KERNEL_CS, GATE_FLAGS);
    idt_set_gate(10, (u32)isr10, KERNEL_CS, GATE_FLAGS);
    idt_set_gate(11, (u32)isr11, KERNEL_CS, GATE_FLAGS);
    idt_set_gate(12, (u32)isr12, KERNEL_CS, GATE_FLAGS);
    idt_set_gate(13, (u32)isr13, KERNEL_CS, GATE_FLAGS);
    idt_set_gate(14, (u32)isr14, KERNEL_CS, GATE_FLAGS);
    idt_set_gate(15, (u32)isr15, KERNEL_CS, GATE_FLAGS);
    idt_set_gate(16, (u32)isr16, KERNEL_CS, GATE_FLAGS);
    idt_set_gate(17, (u32)isr17, KERNEL_CS, GATE_FLAGS);
    idt_set_gate(18, (u32)isr18, KERNEL_CS, GATE_FLAGS);
    idt_set_gate(19, (u32)isr19, KERNEL_CS, GATE_FLAGS);
    idt_set_gate(20, (u32)isr20, KERNEL_CS, GATE_FLAGS);
    idt_set_gate(21, (u32)isr21, KERNEL_CS, GATE_FLAGS);
    idt_set_gate(22, (u32)isr22, KERNEL_CS, GATE_FLAGS);
    idt_set_gate(23, (u32)isr23, KERNEL_CS, GATE_FLAGS);
    idt_set_gate(24, (u32)isr24, KERNEL_CS, GATE_FLAGS);
    idt_set_gate(25, (u32)isr25, KERNEL_CS, GATE_FLAGS);
    idt_set_gate(26, (u32)isr26, KERNEL_CS, GATE_FLAGS);
    idt_set_gate(27, (u32)isr27, KERNEL_CS, GATE_FLAGS);
    idt_set_gate(28, (u32)isr28, KERNEL_CS, GATE_FLAGS);
    idt_set_gate(29, (u32)isr29, KERNEL_CS, GATE_FLAGS);
    idt_set_gate(30, (u32)isr30, KERNEL_CS, GATE_FLAGS);
    idt_set_gate(31, (u32)isr31, KERNEL_CS, GATE_FLAGS);

    /* IRQs were remapped to 0x20-0x2F by pic_remap() in kernel_main. */
    idt_set_gate(32, (u32)irq0,  KERNEL_CS, GATE_FLAGS);
    idt_set_gate(33, (u32)irq1,  KERNEL_CS, GATE_FLAGS);
    idt_set_gate(34, (u32)irq2,  KERNEL_CS, GATE_FLAGS);
    idt_set_gate(35, (u32)irq3,  KERNEL_CS, GATE_FLAGS);
    idt_set_gate(36, (u32)irq4,  KERNEL_CS, GATE_FLAGS);
    idt_set_gate(37, (u32)irq5,  KERNEL_CS, GATE_FLAGS);
    idt_set_gate(38, (u32)irq6,  KERNEL_CS, GATE_FLAGS);
    idt_set_gate(39, (u32)irq7,  KERNEL_CS, GATE_FLAGS);
    idt_set_gate(40, (u32)irq8,  KERNEL_CS, GATE_FLAGS);
    idt_set_gate(41, (u32)irq9,  KERNEL_CS, GATE_FLAGS);
    idt_set_gate(42, (u32)irq10, KERNEL_CS, GATE_FLAGS);
    idt_set_gate(43, (u32)irq11, KERNEL_CS, GATE_FLAGS);
    idt_set_gate(44, (u32)irq12, KERNEL_CS, GATE_FLAGS);
    idt_set_gate(45, (u32)irq13, KERNEL_CS, GATE_FLAGS);
    idt_set_gate(46, (u32)irq14, KERNEL_CS, GATE_FLAGS);
    idt_set_gate(47, (u32)irq15, KERNEL_CS, GATE_FLAGS);
}

void register_interrupt_handler(u8 n, isr_handler_t handler) {
    interrupt_handlers[n] = handler;
}

/* CPU exceptions (vectors 0-31): unrecoverable here -> panic with a full
 * register dump, then halt. Returns (formally) the same frame to satisfy the
 * shared stub signature, but never actually returns. */
u32 isr_handler(registers_t *regs) {
    vga_set_color(VGA_WHITE, VGA_RED);
    kprintf("\n*** KERNEL PANIC ***\n");
    kprintf("  %s (int %u, err 0x%x)\n",
            regs->int_no < 32 ? exception_messages[regs->int_no] : "Unknown",
            regs->int_no, regs->err_code);
    kprintf("  eip=0x%x  cs=0x%x  eflags=0x%x\n", regs->eip, regs->cs, regs->eflags);
    kprintf("  eax=0x%x ebx=0x%x ecx=0x%x edx=0x%x\n",
            regs->eax, regs->ebx, regs->ecx, regs->edx);
    kprintf("  esi=0x%x edi=0x%x ebp=0x%x esp=0x%x\n",
            regs->esi, regs->edi, regs->ebp, regs->esp);
    kprintf("  System halted.\n");

    /* Halt forever; better a frozen, readable screen than a reboot loop. */
    for (;;) __asm__ volatile ("cli; hlt");
    return (u32)(uintptr_t)regs; /* unreachable */
}

/* Hardware IRQs (vectors 32-47): dispatch to a device callback, send the PIC an
 * EOI, then give the scheduler a chance to preempt. The returned stack pointer
 * is what the assembly stub resumes on -- so if the timer requested a reschedule
 * this returns the *next* task's frame and the context switch happens for free. */
u32 irq_handler(registers_t *regs) {
    if (interrupt_handlers[regs->int_no]) {
        interrupt_handlers[regs->int_no](regs);
    }
    /* Acknowledge the PIC so the same line can fire again. Do this BEFORE the
     * possible task switch, since after switching we may not return here. */
    pic_send_eoi((u8)(regs->int_no - IRQ_BASE));

    /* Preempt if the timer (or a yield) asked for it; otherwise stay put. */
    return scheduler_on_interrupt_return((u32)(uintptr_t)regs);
}
