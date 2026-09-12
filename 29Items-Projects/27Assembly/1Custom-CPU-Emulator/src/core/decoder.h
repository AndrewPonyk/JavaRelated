/*
 * decoder.h — x86-64 subset instruction decoder + disassembler.
 *
 * Translates raw guest bytes at a given address into a structured
 * `instruction_t`. The v1 decoder handles legacy prefixes, the REX prefix
 * (0x40-0x4F), ModR/M, SIB, displacement and immediate fields for the opcode
 * subset enumerated in common/types.h (opcode_t).
 *
 * Decoding NEVER reads past the end of guest memory; a truncated encoding
 * yields EMU_ERR_DECODE and an unsupported encoding yields EMU_ERR_INVALID_OPCODE.
 */
#ifndef CPUEMU_CORE_DECODER_H
#define CPUEMU_CORE_DECODER_H

#include "common/types.h"
#include "core/memory.h"

#define INSN_MAX_LEN 15  /* x86 instructions are at most 15 bytes */

/* A single decoded operand. */
typedef struct {
    operand_kind_t kind;
    uint8_t        width;     /* operand size in bytes (1/2/4/8)            */

    /* OPERAND_REG */
    reg_id_t       reg;

    /* OPERAND_IMM */
    uint64_t       imm;       /* zero/sign-extended immediate                */

    /* OPERAND_MEM: effective address = base + index*scale + disp           */
    reg_id_t       mem_base;  /* REG_NONE if absent                         */
    reg_id_t       mem_index; /* REG_NONE if absent                         */
    uint8_t        mem_scale; /* 1, 2, 4, or 8                              */
    int64_t        mem_disp;  /* signed displacement                        */
    bool           rip_relative;
} operand_t;

/* A fully decoded instruction. */
typedef struct {
    opcode_t  op;
    operand_t dst;            /* destination / first operand                 */
    operand_t src;            /* source / second operand                     */
    uint8_t   length;         /* total encoded length in bytes               */
    bool      rex_w;          /* 64-bit operand override was present          */
    uint8_t   raw[INSN_MAX_LEN]; /* raw bytes, for the disassembly/hex view  */
} instruction_t;

/* Decode the instruction at guest address `addr`. On success fills `*out` and
 * sets out->length. */
emu_status_t decode_instruction(const memory_t *mem, uint64_t addr, instruction_t *out);

/* Mnemonic string for an opcode ("mov", "add", ...). */
const char *opcode_mnemonic(opcode_t op);

/* Render Intel-syntax disassembly into `buf` (e.g. "add rax, 0x10"). Returns the
 * number of characters written (excluding NUL), or a negative value on error. */
int disasm_format(const instruction_t *insn, char *buf, size_t buflen);

#endif /* CPUEMU_CORE_DECODER_H */
