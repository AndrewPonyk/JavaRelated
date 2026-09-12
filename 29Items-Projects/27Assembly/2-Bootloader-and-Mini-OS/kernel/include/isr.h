/* =============================================================================
 *  isr.h  --  Interrupt Service Routines & IRQ dispatch
 *
 *  The assembly stubs (cpu/isr_stubs.asm) push a uniform register frame and
 *  call into the C dispatchers below. CPU exceptions are vectors 0-31; the
 *  PIC-remapped hardware IRQs are vectors 32-47 (0x20-0x2F).
 * ===========================================================================*/
#ifndef MINIOS_ISR_H
#define MINIOS_ISR_H

#include "types.h"

#define IRQ0  32                /* PIT timer            */
#define IRQ1  33                /* keyboard             */
#define IRQ_BASE 32

/* Register frame pushed by the common assembly stub, in push order. Lets a
 * handler inspect/modify CPU state at the point of interrupt. A pointer to this
 * frame doubles as a task's saved kernel stack pointer, which is what makes
 * preemptive context switching work: the handler returns the stack pointer of
 * the frame to resume, and the stub restores it (see scheduler.c / isr_stubs.asm).
 *
 * NOTE: useresp/ss are only pushed by the CPU on a privilege change (ring3->0).
 * This kernel runs entirely in ring 0, so for our interrupts the frame actually
 * ends at `eflags`; those two trailing fields are unused here. */
typedef struct {
    u32 ds;                              /* data segment, saved by the stub   */
    u32 edi, esi, ebp, esp, ebx, edx, ecx, eax;  /* pushed by `pusha`         */
    u32 int_no, err_code;                /* vector number + CPU error code    */
    u32 eip, cs, eflags, useresp, ss;    /* pushed automatically by the CPU   */
} __attribute__((packed)) registers_t;

/* A device/handler callback registered against a specific IRQ line. */
typedef void (*isr_handler_t)(registers_t *regs);

void isr_install(void);                              /* wire up vectors 0-47 */
void register_interrupt_handler(u8 n, isr_handler_t handler);

/* C entry points called from the assembly stubs. Both return the kernel stack
 * pointer to resume execution on: normally the same frame they were handed, but
 * the IRQ path may return a *different* task's frame to perform a context
 * switch. The assembly stub does `mov esp, eax` with this return value. */
u32 isr_handler(registers_t *regs);     /* CPU exceptions 0-31 */
u32 irq_handler(registers_t *regs);     /* hardware IRQs 32-47 (sends EOI) */

#endif /* MINIOS_ISR_H */
