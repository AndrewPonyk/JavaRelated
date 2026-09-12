/*
 * registers.h — Architectural register file + RFLAGS  (THE STATE SCHEMA).
 *
 * This struct is the canonical "schema" of the machine's persistent state
 * (alongside memory_t). It is what gets serialized into a snapshot by loader.c
 * and what the visualizer reads each frame.
 *
 * x86-64 sub-register semantics implemented by reg_read/reg_write:
 *   - 8/16-bit writes PRESERVE the upper bits of the 64-bit register.
 *   - 32-bit writes ZERO-EXTEND into the full 64-bit register (real HW quirk).
 *   - 64-bit writes replace the whole register.
 */
#ifndef CPUEMU_CORE_REGISTERS_H
#define CPUEMU_CORE_REGISTERS_H

#include "common/types.h"

typedef struct {
    uint64_t gpr[REG_COUNT]; /* indexed by reg_id_t                       */
    uint64_t rip;            /* instruction pointer                       */
    uint64_t rflags;         /* condition/control flags (see flag_mask_t) */
} register_file_t;

/* Reset to power-on state: all GPRs zero, RFLAGS with the reserved bit-1 set. */
void regfile_reset(register_file_t *rf);

/* Read `width` (1/2/4/8) low bytes of register `id`, zero-extended to 64 bits. */
uint64_t reg_read(const register_file_t *rf, reg_id_t id, uint8_t width);

/* Write `width` low bytes of register `id` following x86-64 sub-register rules. */
void reg_write(register_file_t *rf, reg_id_t id, uint8_t width, uint64_t value);

/* Condition-flag accessors. */
bool flag_get(const register_file_t *rf, flag_mask_t flag);
void flag_set(register_file_t *rf, flag_mask_t flag, bool value);

/* Canonical assembler name for a register at a given width (e.g. RAX/EAX/AX/AL). */
const char *reg_name(reg_id_t id, uint8_t width);

#endif /* CPUEMU_CORE_REGISTERS_H */
