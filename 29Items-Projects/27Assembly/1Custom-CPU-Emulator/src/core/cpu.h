/*
 * cpu.h — The aggregate machine and its fetch-decode-execute-retire loop.
 *
 * `struct cpu` ties together the register file, memory, IDT, and timing model.
 * It is the single source of truth for machine state; the GUI reads it and the
 * loader serializes it. This is the orchestration layer of the core engine.
 */
#ifndef CPUEMU_CORE_CPU_H
#define CPUEMU_CORE_CPU_H

#include "common/types.h"
#include "core/registers.h"
#include "core/memory.h"
#include "core/interrupts.h"
#include "core/timing.h"
#include "core/decoder.h"

struct cpu {
    register_file_t regs;
    memory_t        mem;
    idt_t           idt;
    timing_state_t  timing;
    cpu_model_t     model;

    bool            halted;       /* set by HLT or a fatal unhandled fault   */
    uint64_t        instret;      /* instructions retired                    */
    emu_status_t    last_status;  /* status of the most recent step          */
    instruction_t   last_insn;    /* last decoded instruction (for the UI)   */
};

/* Allocate a CPU with `mem_size` bytes of RAM for `model`. NULL on failure. */
cpu_t *cpu_create(size_t mem_size, cpu_model_t model);

/* Free a CPU and its memory. */
void cpu_destroy(cpu_t *cpu);

/* Reset registers, flags, timing, and IDT (memory contents are preserved). */
void cpu_reset(cpu_t *cpu);

/* Copy `len` bytes of code into memory at `load_addr` and set RIP = load_addr. */
emu_status_t cpu_load_program(cpu_t *cpu, const uint8_t *code, size_t len,
                              uint64_t load_addr);

/* Execute exactly one instruction (fetch→decode→execute→retire→account). */
emu_status_t cpu_step(cpu_t *cpu);

/* Run until HLT, fault, or `max_steps` (0 = unlimited). Returns the stop reason. */
emu_status_t cpu_run(cpu_t *cpu, uint64_t max_steps);

/* Stack helpers (used by PUSH/POP/CALL/RET and the interrupt path). */
emu_status_t cpu_push(cpu_t *cpu, uint64_t value, uint8_t width);
emu_status_t cpu_pop(cpu_t *cpu, uint64_t *out, uint8_t width);

#endif /* CPUEMU_CORE_CPU_H */
