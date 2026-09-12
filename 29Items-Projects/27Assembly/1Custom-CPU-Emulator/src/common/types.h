/*
 * types.h — Foundational types, enums, and status codes for the emulator.
 *
 * This header is the shared vocabulary of the whole project. It depends only on
 * the C standard library and must never include any `core/`, `gui/`, or `loader/`
 * header (dependencies point inward — see docs/ARCHITECTURE.md §2.1).
 */
#ifndef CPUEMU_COMMON_TYPES_H
#define CPUEMU_COMMON_TYPES_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/* Forward declaration of the aggregate machine state. The full definition lives
 * in core/cpu.h; modules that only need a pointer (e.g. interrupts) use this. */
typedef struct cpu cpu_t;

/* ------------------------------------------------------------------------- *
 * Operand widths (in bytes). x86-64 operates on 8/16/32/64-bit operands.
 * ------------------------------------------------------------------------- */
#define WIDTH_BYTE  1u
#define WIDTH_WORD  2u
#define WIDTH_DWORD 4u
#define WIDTH_QWORD 8u

/* ------------------------------------------------------------------------- *
 * General-purpose registers (architectural index order).
 * The enum value doubles as the index into register_file_t.gpr[].
 * ------------------------------------------------------------------------- */
typedef enum {
    REG_RAX = 0, REG_RCX, REG_RDX, REG_RBX,
    REG_RSP,     REG_RBP, REG_RSI, REG_RDI,
    REG_R8,      REG_R9,  REG_R10, REG_R11,
    REG_R12,     REG_R13, REG_R14, REG_R15,
    REG_COUNT,
    REG_NONE = 0xFF   /* sentinel: "no register" for operands */
} reg_id_t;

/* ------------------------------------------------------------------------- *
 * RFLAGS condition/control bits, expressed as masks at their real bit
 * positions (matches the x86 RFLAGS layout exactly).
 * ------------------------------------------------------------------------- */
typedef enum {
    FLAG_CF = 1u << 0,   /* carry        (unsigned overflow)          */
    FLAG_PF = 1u << 2,   /* parity       (even parity of low 8 bits)  */
    FLAG_AF = 1u << 4,   /* aux carry    (carry out of bit 3, BCD)    */
    FLAG_ZF = 1u << 6,   /* zero                                       */
    FLAG_SF = 1u << 7,   /* sign         (MSB of result)              */
    FLAG_TF = 1u << 8,   /* trap         (single-step)                */
    FLAG_IF = 1u << 9,   /* interrupt enable                          */
    FLAG_DF = 1u << 10,  /* direction                                 */
    FLAG_OF = 1u << 11   /* overflow     (signed overflow)            */
} flag_mask_t;

/* ------------------------------------------------------------------------- *
 * Decoded instruction mnemonics (the v1 supported subset). Extend by adding
 * a row here, a decode entry in decoder.c, and a case in cpu_execute().
 * ------------------------------------------------------------------------- */
typedef enum {
    OP_NOP = 0,
    OP_MOV,  OP_LEA,
    OP_ADD,  OP_SUB,  OP_ADC, OP_SBB,
    OP_AND,  OP_OR,   OP_XOR, OP_NOT, OP_NEG,
    OP_CMP,  OP_TEST,
    OP_INC,  OP_DEC,
    OP_SHL,  OP_SHR,  OP_SAR,
    OP_MUL,  OP_IMUL, OP_DIV, OP_IDIV,
    OP_PUSH, OP_POP,
    OP_JMP,  OP_JE,   OP_JNE, OP_JL, OP_JLE, OP_JG, OP_JGE,
    OP_CALL, OP_RET,  OP_LOOP,
    OP_INT,  OP_IRET,
    OP_HLT,
    OP_UNKNOWN,
    OP_COUNT
} opcode_t;

/* ------------------------------------------------------------------------- *
 * Operand kinds for the decoded form.
 * ------------------------------------------------------------------------- */
typedef enum {
    OPERAND_NONE = 0,
    OPERAND_REG,   /* a general-purpose register                      */
    OPERAND_IMM,   /* an immediate constant                            */
    OPERAND_MEM    /* a memory reference: [base + index*scale + disp]  */
} operand_kind_t;

/* ------------------------------------------------------------------------- *
 * Modeled microarchitectures for the timing simulator. Order = oldest→newest.
 * ------------------------------------------------------------------------- */
typedef enum {
    CPU_MODEL_8086 = 0,
    CPU_MODEL_I486,
    CPU_MODEL_PENTIUM,
    CPU_MODEL_CORE2,
    CPU_MODEL_SKYLAKE,
    CPU_MODEL_COUNT
} cpu_model_t;

/* ------------------------------------------------------------------------- *
 * Unified status / error codes. Every fallible API returns one of these.
 * See docs/ARCHITECTURE.md §2.6 for the error-handling philosophy.
 * ------------------------------------------------------------------------- */
typedef enum {
    EMU_OK = 0,               /* success                                   */
    EMU_ERR_NULL,             /* NULL/invalid argument at an API boundary  */
    EMU_ERR_MEM_BOUNDS,       /* guest memory access out of range          */
    EMU_ERR_DECODE,           /* could not decode (truncated/garbage)      */
    EMU_ERR_INVALID_OPCODE,   /* decoded to an unsupported opcode (#UD)    */
    EMU_ERR_DIV_ZERO,         /* guest divide by zero (#DE)                */
    EMU_ERR_IO,               /* host file/IO failure                      */
    EMU_ERR_NOMEM,            /* host allocation failure                   */
    EMU_ERR_HALT              /* guest executed HLT (clean stop)           */
} emu_status_t;

/* Human-readable name for a status code (defined in log.c). */
const char *emu_status_str(emu_status_t status);

#endif /* CPUEMU_COMMON_TYPES_H */
