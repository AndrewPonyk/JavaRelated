/*
 * interrupts.c — IDT management and interrupt/exception delivery.
 */
#include "core/interrupts.h"
#include "core/cpu.h"
#include "core/registers.h"
#include "common/log.h"

#include <string.h>

void idt_init(idt_t *idt) {
    if (idt != NULL) {
        memset(idt, 0, sizeof *idt);
    }
}

void idt_set_handler(idt_t *idt, uint8_t vector, uint64_t handler) {
    if (idt != NULL) {
        idt->entries[vector].handler = handler;
        idt->entries[vector].present = true;
    }
}

bool idt_get_handler(const idt_t *idt, uint8_t vector, uint64_t *handler_out) {
    if (idt == NULL || !idt->entries[vector].present) {
        return false;
    }
    if (handler_out != NULL) {
        *handler_out = idt->entries[vector].handler;
    }
    return true;
}

/* Map a vector to the status the run-loop should report when it's unhandled. */
static emu_status_t status_for_vector(uint8_t vector) {
    switch (vector) {
        case VEC_DIVIDE_ERROR:    return EMU_ERR_DIV_ZERO;
        case VEC_INVALID_OPCODE:  return EMU_ERR_INVALID_OPCODE;
        case VEC_GENERAL_PROTECT: return EMU_ERR_MEM_BOUNDS;
        default:                  return EMU_ERR_HALT;
    }
}

emu_status_t cpu_raise_interrupt(cpu_t *cpu, uint8_t vector) {
    if (cpu == NULL) {
        return EMU_ERR_NULL;
    }

    uint64_t handler = 0;
    if (!idt_get_handler(&cpu->idt, vector, &handler)) {
        /* No handler: this is a fatal guest fault. Stop and let main report it. */
        log_warn("unhandled vector %u (%s); halting", vector,
                 emu_status_str(status_for_vector(vector)));
        cpu->halted = true;
        return status_for_vector(vector);
    }

    /* Interrupt frame (simplified): push RFLAGS then the return RIP. */
    emu_status_t st = cpu_push(cpu, cpu->regs.rflags, WIDTH_QWORD);
    if (st != EMU_OK) return st;
    st = cpu_push(cpu, cpu->regs.rip, WIDTH_QWORD);
    if (st != EMU_OK) return st;

    flag_set(&cpu->regs, FLAG_IF, false); /* mask further interrupts */
    cpu->regs.rip = handler;
    log_debug("entered handler for vector %u at 0x%llx", vector,
              (unsigned long long)handler);
    return EMU_OK;
}
