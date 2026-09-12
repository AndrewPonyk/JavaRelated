/*
 * decoder.c — x86-64 subset decoder.
 *
 * Pipeline:  legacy prefixes → REX → opcode → ModR/M → SIB → disp → immediate.
 * This v1 handles a representative slice of the ISA end-to-end (enough to run the
 * test fixtures) and returns EMU_ERR_INVALID_OPCODE for anything outside the
 * subset, so the CPU can raise a clean #UD. Extending it = adding opcode rows.
 *
 * NOTE: This is deliberately a teaching-grade decoder. Full ModR/M/SIB coverage
 * (all addressing forms, group opcodes, two-byte 0x0F maps) is TODO — see the
 * markers below.
 */
#include "core/decoder.h"
#include "core/registers.h"

#include <stdio.h>
#include <string.h>

static const char *const k_mnemonic[OP_COUNT] = {
    [OP_NOP]="nop", [OP_MOV]="mov", [OP_LEA]="lea",
    [OP_ADD]="add", [OP_SUB]="sub", [OP_ADC]="adc", [OP_SBB]="sbb",
    [OP_AND]="and", [OP_OR]="or",  [OP_XOR]="xor", [OP_NOT]="not", [OP_NEG]="neg",
    [OP_CMP]="cmp", [OP_TEST]="test",
    [OP_INC]="inc", [OP_DEC]="dec",
    [OP_SHL]="shl", [OP_SHR]="shr", [OP_SAR]="sar",
    [OP_MUL]="mul", [OP_IMUL]="imul", [OP_DIV]="div", [OP_IDIV]="idiv",
    [OP_PUSH]="push", [OP_POP]="pop",
    [OP_JMP]="jmp", [OP_JE]="je", [OP_JNE]="jne", [OP_JL]="jl", [OP_JLE]="jle",
    [OP_JG]="jg", [OP_JGE]="jge",
    [OP_CALL]="call", [OP_RET]="ret", [OP_LOOP]="loop",
    [OP_INT]="int", [OP_IRET]="iret",
    [OP_HLT]="hlt", [OP_UNKNOWN]="(bad)"
};

const char *opcode_mnemonic(opcode_t op) {
    return (op < OP_COUNT && k_mnemonic[op]) ? k_mnemonic[op] : "(bad)";
}

/* ---- small helpers for building operands ---- */
static operand_t op_reg(reg_id_t r, uint8_t width) {
    operand_t o; memset(&o, 0, sizeof o);
    o.kind = OPERAND_REG; o.reg = r; o.width = width;
    o.mem_base = REG_NONE; o.mem_index = REG_NONE;
    return o;
}
static operand_t op_imm(uint64_t v, uint8_t width) {
    operand_t o; memset(&o, 0, sizeof o);
    o.kind = OPERAND_IMM; o.imm = v; o.width = width;
    o.mem_base = REG_NONE; o.mem_index = REG_NONE;
    return o;
}
static operand_t op_none(void) {
    operand_t o; memset(&o, 0, sizeof o);
    o.kind = OPERAND_NONE; o.reg = REG_NONE;
    o.mem_base = REG_NONE; o.mem_index = REG_NONE;
    return o;
}

/* Decode a ModR/M (and any SIB/displacement) into `rm`, and report the `reg`
 * field separately. Advances *pos. Returns EMU_OK or EMU_ERR_DECODE. */
static emu_status_t parse_modrm(const uint8_t *buf, size_t avail, size_t *pos,
                                bool rex_r, bool rex_x, bool rex_b,
                                uint8_t width, operand_t *rm, reg_id_t *reg) {
    if (*pos >= avail) return EMU_ERR_DECODE;
    uint8_t modrm = buf[(*pos)++];
    uint8_t mod = (modrm >> 6) & 3u;
    uint8_t rm_f = modrm & 7u;

    *reg = (reg_id_t)(((modrm >> 3) & 7u) | (rex_r ? 8u : 0u));

    if (mod == 3u) { /* register-direct */
        *rm = op_reg((reg_id_t)(rm_f | (rex_b ? 8u : 0u)), width);
        return EMU_OK;
    }

    /* memory form */
    memset(rm, 0, sizeof *rm);
    rm->kind = OPERAND_MEM; rm->width = width;
    rm->mem_base = REG_NONE; rm->mem_index = REG_NONE; rm->mem_scale = 1;

    if (rm_f == 4u) { /* SIB byte follows */
        if (*pos >= avail) return EMU_ERR_DECODE;
        uint8_t sib = buf[(*pos)++];
        uint8_t scale = 1u << ((sib >> 6) & 3u);
        uint8_t index = ((sib >> 3) & 7u) | (rex_x ? 8u : 0u);
        uint8_t base  = (sib & 7u) | (rex_b ? 8u : 0u);
        rm->mem_scale = scale;
        if (((sib >> 3) & 7u) != 4u) { /* index==100 means "no index" */
            rm->mem_index = (reg_id_t)index;
        }
        rm->mem_base = (reg_id_t)base;
    } else if (mod == 0u && rm_f == 5u) { /* RIP-relative disp32 */
        rm->rip_relative = true;
        rm->mem_base = REG_NONE;
    } else {
        rm->mem_base = (reg_id_t)(rm_f | (rex_b ? 8u : 0u));
    }

    /* displacement */
    if (mod == 1u) {              /* disp8 (sign-extended) */
        if (*pos >= avail) return EMU_ERR_DECODE;
        rm->mem_disp = (int8_t)buf[(*pos)++];
    } else if (mod == 2u || (mod == 0u && rm_f == 5u)) { /* disp32 */
        if (*pos + 4u > avail) return EMU_ERR_DECODE;
        /* Assemble little-endian explicitly (host-endianness independent), then
         * sign-extend through int32_t into the int64 displacement. */
        uint32_t d = (uint32_t)buf[*pos] | ((uint32_t)buf[*pos + 1] << 8) |
                     ((uint32_t)buf[*pos + 2] << 16) | ((uint32_t)buf[*pos + 3] << 24);
        rm->mem_disp = (int32_t)d;
        *pos += 4;
    }
    return EMU_OK;
}

static uint64_t read_imm(const uint8_t *buf, size_t *pos, uint8_t n) {
    uint64_t v = 0;
    for (uint8_t i = 0; i < n; ++i) {
        v |= (uint64_t)buf[*pos + i] << (8u * i);
    }
    *pos += n;
    return v;
}

emu_status_t decode_instruction(const memory_t *mem, uint64_t addr, instruction_t *out) {
    if (mem == NULL || out == NULL) {
        return EMU_ERR_NULL;
    }
    memset(out, 0, sizeof *out);
    out->op = OP_UNKNOWN;
    out->dst = op_none();
    out->src = op_none();

    /* Snapshot up to 15 bytes of instruction stream (bounded by memory end). */
    uint8_t buf[INSN_MAX_LEN] = {0};
    size_t avail = 0;
    for (; avail < INSN_MAX_LEN; ++avail) {
        uint64_t b;
        if (mem_read_width(mem, addr + avail, WIDTH_BYTE, &b) != EMU_OK) {
            break;
        }
        buf[avail] = (uint8_t)b;
    }
    if (avail == 0) {
        return EMU_ERR_MEM_BOUNDS;
    }

    size_t pos = 0;
    uint8_t width = WIDTH_DWORD; /* default operand size in 64-bit mode */

    /* ---- legacy prefixes (only operand-size override is acted on here) ---- */
    bool done_prefix = false;
    while (!done_prefix && pos < avail) {
        switch (buf[pos]) {
            case 0x66: width = WIDTH_WORD; pos++; break;        /* opsize       */
            case 0x67: pos++; break;                             /* addrsize     */
            case 0xF0: case 0xF2: case 0xF3: pos++; break;       /* lock/rep     */
            case 0x2E: case 0x36: case 0x3E: case 0x26:          /* segment      */
            case 0x64: case 0x65: pos++; break;
            default: done_prefix = true; break;
        }
    }

    /* ---- REX prefix ---- */
    bool rex_w = false, rex_r = false, rex_x = false, rex_b = false;
    if (pos < avail && (buf[pos] & 0xF0u) == 0x40u) {
        uint8_t rex = buf[pos++];
        rex_w = (rex & 0x8u) != 0;
        rex_r = (rex & 0x4u) != 0;
        rex_x = (rex & 0x2u) != 0;
        rex_b = (rex & 0x1u) != 0;
        if (rex_w) width = WIDTH_QWORD;
    }
    out->rex_w = rex_w;

    if (pos >= avail) {
        return EMU_ERR_DECODE;
    }
    uint8_t op = buf[pos++];
    reg_id_t reg = REG_NONE;
    operand_t rm;
    memset(&rm, 0, sizeof rm);

    switch (op) {
    case 0x90: out->op = OP_NOP; break;
    case 0xF4: out->op = OP_HLT; break;
    case 0xC3: out->op = OP_RET; break;
    case 0xCF: out->op = OP_IRET; break;

    /* PUSH r64 (50+r) / POP r64 (58+r) — default 64-bit operand in long mode. */
    case 0x50: case 0x51: case 0x52: case 0x53:
    case 0x54: case 0x55: case 0x56: case 0x57:
        out->op = OP_PUSH;
        out->dst = op_reg((reg_id_t)((op - 0x50) | (rex_b ? 8u : 0u)), WIDTH_QWORD);
        break;
    case 0x58: case 0x59: case 0x5A: case 0x5B:
    case 0x5C: case 0x5D: case 0x5E: case 0x5F:
        out->op = OP_POP;
        out->dst = op_reg((reg_id_t)((op - 0x58) | (rex_b ? 8u : 0u)), WIDTH_QWORD);
        break;

    case 0xCD: /* INT ib */
        if (pos >= avail) return EMU_ERR_DECODE;
        out->op = OP_INT;
        out->dst = op_imm(buf[pos++], WIDTH_BYTE);
        break;

    case 0xEB: /* JMP rel8 */
        if (pos >= avail) return EMU_ERR_DECODE;
        out->op = OP_JMP;
        out->dst = op_imm((uint64_t)(int64_t)(int8_t)buf[pos++], WIDTH_BYTE);
        break;
    case 0xE9: /* JMP rel32 */
        if (pos + 4 > avail) return EMU_ERR_DECODE;
        out->op = OP_JMP;
        out->dst = op_imm((uint64_t)(int64_t)(int32_t)read_imm(buf, &pos, 4), WIDTH_DWORD);
        break;
    case 0xE8: /* CALL rel32 */
        if (pos + 4 > avail) return EMU_ERR_DECODE;
        out->op = OP_CALL;
        out->dst = op_imm((uint64_t)(int64_t)(int32_t)read_imm(buf, &pos, 4), WIDTH_DWORD);
        break;

    /* conditional jumps, rel8 */
    case 0x74: case 0x75: case 0x7C: case 0x7E: case 0x7F: case 0x7D: {
        static const opcode_t map[] = {
            [0x74-0x74]=OP_JE, [0x75-0x74]=OP_JNE, [0x7C-0x74]=OP_JL,
            [0x7D-0x74]=OP_JGE, [0x7E-0x74]=OP_JLE, [0x7F-0x74]=OP_JG };
        if (pos >= avail) return EMU_ERR_DECODE;
        out->op = map[op - 0x74];
        out->dst = op_imm((uint64_t)(int64_t)(int8_t)buf[pos++], WIDTH_BYTE);
        break;
    }

    /* MOV r32/64, imm  (B8+r) */
    case 0xB8: case 0xB9: case 0xBA: case 0xBB:
    case 0xBC: case 0xBD: case 0xBE: case 0xBF: {
        reg_id_t r = (reg_id_t)((op - 0xB8) | (rex_b ? 8u : 0u));
        uint8_t immw = (width == WIDTH_QWORD) ? WIDTH_QWORD : width;
        if (pos + immw > avail) return EMU_ERR_DECODE;
        out->op = OP_MOV;
        out->dst = op_reg(r, width);
        out->src = op_imm(read_imm(buf, &pos, immw), immw);
        break;
    }

    /* ALU r/m, r   (opcode .. /r) */
    case 0x01: case 0x29: case 0x09: case 0x21: case 0x31: case 0x39: case 0x89: {
        opcode_t o = OP_ADD;
        switch (op) {
            case 0x01: o = OP_ADD; break; case 0x09: o = OP_OR;  break;
            case 0x21: o = OP_AND; break; case 0x29: o = OP_SUB; break;
            case 0x31: o = OP_XOR; break; case 0x39: o = OP_CMP; break;
            case 0x89: o = OP_MOV; break; default: break;
        }
        emu_status_t st = parse_modrm(buf, avail, &pos, rex_r, rex_x, rex_b, width, &rm, &reg);
        if (st != EMU_OK) return st;
        out->op = o;
        out->dst = rm;                    /* r/m is destination */
        out->src = op_reg(reg, width);    /* reg is source      */
        break;
    }

    /* MOV r, r/m  (0x8B /r) */
    case 0x8B: {
        emu_status_t st = parse_modrm(buf, avail, &pos, rex_r, rex_x, rex_b, width, &rm, &reg);
        if (st != EMU_OK) return st;
        out->op = OP_MOV;
        out->dst = op_reg(reg, width);
        out->src = rm;
        break;
    }

    /* group: ADD/OR/ADC/SBB/AND/SUB/XOR/CMP r/m, imm8  (0x83 /digit ib) */
    case 0x83: {
        emu_status_t st = parse_modrm(buf, avail, &pos, rex_r, rex_x, rex_b, width, &rm, &reg);
        if (st != EMU_OK) return st;
        static const opcode_t grp[8] = { OP_ADD, OP_OR, OP_ADC, OP_SBB,
                                         OP_AND, OP_SUB, OP_XOR, OP_CMP };
        uint8_t digit = (uint8_t)reg & 7u; /* the /digit is the reg field */
        if (pos >= avail) return EMU_ERR_DECODE;
        out->op = grp[digit];
        out->dst = rm;
        out->src = op_imm((uint64_t)(int64_t)(int8_t)buf[pos++], WIDTH_BYTE);
        break;
    }

    default:
        out->op = OP_UNKNOWN;
        out->length = 1;
        memcpy(out->raw, buf, 1);
        return EMU_ERR_INVALID_OPCODE;
    }

    out->length = (uint8_t)pos;
    memcpy(out->raw, buf, pos);
    return EMU_OK;
}

/* -------------------------------------------------------------------------- */
static int fmt_operand(const operand_t *o, char *buf, size_t n) {
    switch (o->kind) {
        case OPERAND_REG:
            return snprintf(buf, n, "%s", reg_name(o->reg, o->width));
        case OPERAND_IMM:
            return snprintf(buf, n, "0x%llx", (unsigned long long)o->imm);
        case OPERAND_MEM:
            if (o->rip_relative) {
                return snprintf(buf, n, "[rip%+lld]", (long long)o->mem_disp);
            }
            if (o->mem_index != REG_NONE) {
                return snprintf(buf, n, "[%s+%s*%u%+lld]",
                                reg_name(o->mem_base, WIDTH_QWORD),
                                reg_name(o->mem_index, WIDTH_QWORD),
                                o->mem_scale, (long long)o->mem_disp);
            }
            return snprintf(buf, n, "[%s%+lld]",
                            reg_name(o->mem_base, WIDTH_QWORD),
                            (long long)o->mem_disp);
        case OPERAND_NONE:
        default:
            return 0;
    }
}

int disasm_format(const instruction_t *insn, char *buf, size_t buflen) {
    if (insn == NULL || buf == NULL || buflen == 0) {
        return -1;
    }
    int n = snprintf(buf, buflen, "%s", opcode_mnemonic(insn->op));
    if (n < 0) {
        return -1;
    }
    size_t off = (size_t)n;
    if (off >= buflen) {
        return (int)off; /* truncated; never index past the buffer */
    }

    char ob[64];
    if (insn->dst.kind != OPERAND_NONE) {
        fmt_operand(&insn->dst, ob, sizeof ob);
        n = snprintf(buf + off, buflen - off, " %s", ob);
        if (n < 0) return (int)off;
        off += (size_t)n;
        if (off >= buflen) return (int)off;
    }
    if (insn->src.kind != OPERAND_NONE) {
        fmt_operand(&insn->src, ob, sizeof ob);
        n = snprintf(buf + off, buflen - off, ", %s", ob);
        if (n < 0) return (int)off;
        off += (size_t)n;
    }
    return (int)off;
}
