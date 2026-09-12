/*
 * interrupts.h — Interrupt Descriptor Table, software/hardware IRQs, exceptions.
 *
 * Models a 256-entry IDT. Software interrupts (`INT n`) and CPU exceptions
 * (#DE divide error = 0, #UD invalid opcode = 6, #GP-like memory fault = 13)
 * funnel through cpu_raise_interrupt(): the CPU pushes RFLAGS + return RIP onto
 * the guest stack and transfers control to the registered handler. `IRET`
 * reverses it. This is how guest *faults* are surfaced without crashing the host
 * (see docs/ARCHITECTURE.md §2.6).
 */
#ifndef CPUEMU_CORE_INTERRUPTS_H
#define CPUEMU_CORE_INTERRUPTS_H

#include "common/types.h"

/* Well-known x86 exception vectors used by the engine. */
enum {
    VEC_DIVIDE_ERROR    = 0,   /* #DE */
    VEC_DEBUG           = 1,   /* #DB (single-step / TF) */
    VEC_INVALID_OPCODE  = 6,   /* #UD */
    VEC_GENERAL_PROTECT = 13,  /* #GP (used here for OOB memory) */
    VEC_MAX             = 256
};

typedef struct {
    uint64_t handler; /* guest address of the handler */
    bool     present; /* false ⇒ unhandled vector     */
} idt_entry_t;

typedef struct {
    idt_entry_t entries[VEC_MAX];
} idt_t;

/* Clear all IDT entries (none present). */
void idt_init(idt_t *idt);

/* Register/replace a handler for `vector`. */
void idt_set_handler(idt_t *idt, uint8_t vector, uint64_t handler);

/* Query a handler; returns false if the vector has no present handler. */
bool idt_get_handler(const idt_t *idt, uint8_t vector, uint64_t *handler_out);

/*
 * Deliver an interrupt/exception to the CPU: push RFLAGS and the current/return
 * RIP, clear IF, and jump to the handler. If no handler is present this returns
 * a status that the run loop treats as a fatal guest fault (and reports).
 */
emu_status_t cpu_raise_interrupt(cpu_t *cpu, uint8_t vector);

#endif /* CPUEMU_CORE_INTERRUPTS_H */
