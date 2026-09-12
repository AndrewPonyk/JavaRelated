/*
 * alu.h — Arithmetic/Logic Unit: results AND exact condition flags.
 *
 * alu_execute is a PURE function: it takes operands + incoming flags and returns
 * the masked result plus the full recomputed RFLAGS. It performs no I/O and
 * touches no global state, which makes it trivially testable with golden truth
 * tables (see tests/unit/test_alu.c) — the highest-value tests in the project.
 */
#ifndef CPUEMU_CORE_ALU_H
#define CPUEMU_CORE_ALU_H

#include "common/types.h"

typedef struct {
    uint64_t result; /* result, masked to the operand width                 */
    uint64_t rflags; /* full recomputed flags (merge of updated + preserved) */
    emu_status_t status; /* EMU_OK, or EMU_ERR_DIV_ZERO for DIV/IDIV by 0    */
} alu_result_t;

/*
 * Execute one ALU operation.
 *   op      — arithmetic/logic opcode (OP_ADD, OP_SUB, OP_AND, OP_SHL, ...)
 *   a, b    — operands (b is the source/second operand; ignored for unary ops)
 *   width   — operand width in bytes (1/2/4/8)
 *   flags_in— current RFLAGS (used by ADC/SBB carry-in; preserved bits kept)
 */
alu_result_t alu_execute(opcode_t op, uint64_t a, uint64_t b,
                         uint8_t width, uint64_t flags_in);

/* Utility: bitmask covering `width` bytes (e.g. width=4 → 0xFFFFFFFF). */
uint64_t alu_width_mask(uint8_t width);

/* Utility: even-parity of the low 8 bits (true ⇒ PF set). */
bool alu_parity8(uint64_t value);

#endif /* CPUEMU_CORE_ALU_H */
