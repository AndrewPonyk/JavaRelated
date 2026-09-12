/*
 * alu.c — Arithmetic/Logic Unit with exact x86 condition-flag computation.
 *
 * Reference for flag semantics: Intel SDM Vol. 1 §3.4.3 and Vol. 2 (per-insn
 * "Flags Affected"). The tricky ones (AF, OF) are derived from the sign/carry
 * relationships rather than by overflowing C integers (which would be UB).
 *
 * All arithmetic is done in uint64_t and masked to the operand width; flags are
 * computed on the masked result. See tests/unit/test_alu.c for the truth tables.
 */
#include "core/alu.h"
#include "common/config.h"

#if CPUEMU_USE_ASM_ALU
/* Optional hand-written NASM fast path (System V AMD64 ABI). See src/asm. */
extern uint64_t alu_fast_add64(uint64_t a, uint64_t b, uint64_t *rflags_out);
#endif

uint64_t alu_width_mask(uint8_t width) {
    switch (width) {
        case WIDTH_BYTE:  return 0xFFull;
        case WIDTH_WORD:  return 0xFFFFull;
        case WIDTH_DWORD: return 0xFFFFFFFFull;
        case WIDTH_QWORD:
        default:          return 0xFFFFFFFFFFFFFFFFull;
    }
}

bool alu_parity8(uint64_t value) {
    uint8_t x = (uint8_t)(value & 0xFFu);
    x ^= (uint8_t)(x >> 4);
    x ^= (uint8_t)(x >> 2);
    x ^= (uint8_t)(x >> 1);
    return (x & 1u) == 0u; /* even number of set bits ⇒ PF set */
}

static uint64_t sign_bit(uint8_t width) {
    return 1ull << (width * 8u - 1u);
}

/* Set SF/ZF/PF from a masked result. */
static uint64_t set_szp(uint64_t flags, uint64_t res, uint8_t width) {
    uint64_t mask = alu_width_mask(width);
    uint64_t r = res & mask;
    flags = (r == 0) ? (flags | FLAG_ZF) : (flags & ~(uint64_t)FLAG_ZF);
    flags = (r & sign_bit(width)) ? (flags | FLAG_SF) : (flags & ~(uint64_t)FLAG_SF);
    flags = alu_parity8(r) ? (flags | FLAG_PF) : (flags & ~(uint64_t)FLAG_PF);
    return flags;
}

static uint64_t set_bit(uint64_t flags, flag_mask_t f, bool on) {
    return on ? (flags | (uint64_t)f) : (flags & ~(uint64_t)f);
}

/* Sign-extend the low `width` bytes of v to a full 64-bit value. */
static uint64_t sign_extend(uint64_t v, uint8_t width) {
    uint64_t m = alu_width_mask(width);
    v &= m;
    if (v & sign_bit(width)) {
        v |= ~m;
    }
    return v;
}

alu_result_t alu_execute(opcode_t op, uint64_t a, uint64_t b,
                         uint8_t width, uint64_t flags_in) {
    /* Defensive: only 1/2/4/8-byte widths are valid (sign_bit() would be UB for
     * width 0). Anything else falls back to 32-bit. */
    if (width != WIDTH_BYTE && width != WIDTH_WORD &&
        width != WIDTH_DWORD && width != WIDTH_QWORD) {
        width = WIDTH_DWORD;
    }
    const uint64_t mask = alu_width_mask(width);
    const uint64_t sb   = sign_bit(width);
    const unsigned bits = width * 8u;
    a &= mask;
    b &= mask;

    alu_result_t out;
    out.result = a;
    out.rflags = flags_in;
    out.status = EMU_OK;

    switch (op) {
    /* ---------------- addition family ---------------- */
    case OP_ADD: {
#if CPUEMU_USE_ASM_ALU
        /* Optional fast path: a true 64-bit host ADD yields CF/PF/AF/ZF/SF/OF
         * (via PUSHFQ) at exactly the x86 RFLAGS positions we use, so for the
         * full-width case we can copy them straight across. */
        if (width == WIDTH_QWORD) {
            uint64_t hwflags = 0;
            uint64_t res_hw = alu_fast_add64(a, b, &hwflags);
            const uint64_t copy = (uint64_t)FLAG_CF | FLAG_PF | FLAG_AF |
                                  FLAG_ZF | FLAG_SF | FLAG_OF;
            out.result = res_hw;
            out.rflags = (flags_in & ~copy) | (hwflags & copy);
            break;
        }
#endif
        uint64_t res = (a + b) & mask;
        bool cf = (width == WIDTH_QWORD) ? ((a + b) < a)
                                         : (((a + b) >> bits) & 1u);
        bool af = ((a ^ b ^ res) & 0x10u) != 0;
        bool of = ((~(a ^ b) & (a ^ res)) & sb) != 0;
        uint64_t f = set_szp(flags_in, res, width);
        f = set_bit(f, FLAG_CF, cf);
        f = set_bit(f, FLAG_AF, af);
        f = set_bit(f, FLAG_OF, of);
        out.result = res; out.rflags = f;
        break;
    }
    case OP_ADC: {
        uint64_t cin = (flags_in & FLAG_CF) ? 1u : 0u;
        uint64_t t   = a + b;
        uint64_t res = (t + cin) & mask;
        bool cf = (width == WIDTH_QWORD)
                      ? ((t < a) || ((t + cin) < t))
                      : ((((a + b + cin) >> bits) & 1u) != 0);
        bool af = ((a ^ b ^ res) & 0x10u) != 0;
        bool of = ((~(a ^ b) & (a ^ res)) & sb) != 0;
        uint64_t f = set_szp(flags_in, res, width);
        f = set_bit(f, FLAG_CF, cf);
        f = set_bit(f, FLAG_AF, af);
        f = set_bit(f, FLAG_OF, of);
        out.result = res; out.rflags = f;
        break;
    }
    case OP_INC: { /* like ADD a,1 but CF is preserved */
        uint64_t res = (a + 1u) & mask;
        bool af = ((a ^ 1u ^ res) & 0x10u) != 0;
        bool of = ((~(a ^ 1u) & (a ^ res)) & sb) != 0;
        uint64_t f = set_szp(flags_in, res, width);
        f = set_bit(f, FLAG_AF, af);
        f = set_bit(f, FLAG_OF, of);
        out.result = res; out.rflags = f; /* CF untouched */
        break;
    }

    /* ---------------- subtraction family ---------------- */
    case OP_SUB:
    case OP_CMP: {
        uint64_t res = (a - b) & mask;
        bool cf = a < b;                          /* unsigned borrow */
        bool af = ((a ^ b ^ res) & 0x10u) != 0;
        bool of = (((a ^ b) & (a ^ res)) & sb) != 0;
        uint64_t f = set_szp(flags_in, res, width);
        f = set_bit(f, FLAG_CF, cf);
        f = set_bit(f, FLAG_AF, af);
        f = set_bit(f, FLAG_OF, of);
        out.result = (op == OP_CMP) ? a : res;    /* CMP discards result */
        out.rflags = f;
        break;
    }
    case OP_SBB: {
        uint64_t cin = (flags_in & FLAG_CF) ? 1u : 0u;
        uint64_t res = (a - b - cin) & mask;
        bool cf = (a < b) || ((a - b) < cin);
        bool af = ((a ^ b ^ res) & 0x10u) != 0;
        bool of = (((a ^ b) & (a ^ res)) & sb) != 0;
        uint64_t f = set_szp(flags_in, res, width);
        f = set_bit(f, FLAG_CF, cf);
        f = set_bit(f, FLAG_AF, af);
        f = set_bit(f, FLAG_OF, of);
        out.result = res; out.rflags = f;
        break;
    }
    case OP_DEC: { /* like SUB a,1 but CF preserved */
        uint64_t res = (a - 1u) & mask;
        bool af = ((a ^ 1u ^ res) & 0x10u) != 0;
        bool of = (((a ^ 1u) & (a ^ res)) & sb) != 0;
        uint64_t f = set_szp(flags_in, res, width);
        f = set_bit(f, FLAG_AF, af);
        f = set_bit(f, FLAG_OF, of);
        out.result = res; out.rflags = f;
        break;
    }
    case OP_NEG: { /* 0 - a */
        uint64_t res = (0u - a) & mask;
        bool cf = (a != 0);
        bool of = (a == sb);                      /* only min-signed overflows */
        bool af = ((a ^ res) & 0x10u) != 0;
        uint64_t f = set_szp(flags_in, res, width);
        f = set_bit(f, FLAG_CF, cf);
        f = set_bit(f, FLAG_AF, af);
        f = set_bit(f, FLAG_OF, of);
        out.result = res; out.rflags = f;
        break;
    }

    /* ---------------- logic family (CF=OF=0, AF undefined→0) ------------- */
    case OP_AND:
    case OP_TEST: {
        uint64_t res = (a & b) & mask;
        uint64_t f = set_szp(flags_in, res, width);
        f = set_bit(f, FLAG_CF, false);
        f = set_bit(f, FLAG_OF, false);
        f = set_bit(f, FLAG_AF, false);
        out.result = (op == OP_TEST) ? a : res;
        out.rflags = f;
        break;
    }
    case OP_OR: {
        uint64_t res = (a | b) & mask;
        uint64_t f = set_szp(flags_in, res, width);
        f = set_bit(f, FLAG_CF, false);
        f = set_bit(f, FLAG_OF, false);
        f = set_bit(f, FLAG_AF, false);
        out.result = res; out.rflags = f;
        break;
    }
    case OP_XOR: {
        uint64_t res = (a ^ b) & mask;
        uint64_t f = set_szp(flags_in, res, width);
        f = set_bit(f, FLAG_CF, false);
        f = set_bit(f, FLAG_OF, false);
        f = set_bit(f, FLAG_AF, false);
        out.result = res; out.rflags = f;
        break;
    }
    case OP_NOT: /* NOT does not affect flags */
        out.result = (~a) & mask;
        out.rflags = flags_in;
        break;

    /* ---------------- shifts (count masked like x86) -------------------- */
    case OP_SHL: {
        unsigned cnt = (unsigned)(b & (width == WIDTH_QWORD ? 0x3Fu : 0x1Fu));
        if (cnt == 0) { out.result = a; out.rflags = flags_in; break; }
        uint64_t res = (a << cnt) & mask;
        bool cf = (cnt <= bits) ? (((a >> (bits - cnt)) & 1u) != 0) : false;
        uint64_t f = set_szp(flags_in, res, width);
        f = set_bit(f, FLAG_CF, cf);
        if (cnt == 1) f = set_bit(f, FLAG_OF, (((res & sb) != 0) ^ cf));
        out.result = res; out.rflags = f;
        break;
    }
    case OP_SHR: {
        unsigned cnt = (unsigned)(b & (width == WIDTH_QWORD ? 0x3Fu : 0x1Fu));
        if (cnt == 0) { out.result = a; out.rflags = flags_in; break; }
        uint64_t res = (a & mask) >> cnt;
        bool cf = ((a >> (cnt - 1)) & 1u) != 0;
        uint64_t f = set_szp(flags_in, res, width);
        f = set_bit(f, FLAG_CF, cf);
        if (cnt == 1) f = set_bit(f, FLAG_OF, (a & sb) != 0); /* MSB of source */
        out.result = res; out.rflags = f;
        break;
    }
    case OP_SAR: {
        unsigned cnt = (unsigned)(b & (width == WIDTH_QWORD ? 0x3Fu : 0x1Fu));
        if (cnt == 0) { out.result = a; out.rflags = flags_in; break; }
        uint64_t se = sign_extend(a, width);
        uint64_t res = ((uint64_t)((int64_t)se >> cnt)) & mask;
        bool cf = ((se >> (cnt - 1)) & 1u) != 0;
        uint64_t f = set_szp(flags_in, res, width);
        f = set_bit(f, FLAG_CF, cf);
        if (cnt == 1) f = set_bit(f, FLAG_OF, false); /* SAR by 1 clears OF */
        out.result = res; out.rflags = f;
        break;
    }

    /* ---------------- multiply / divide (simplified) ------------------- */
    case OP_MUL: { /* unsigned; CF=OF set when the high half is non-zero */
        uint64_t res, hi;
        if (width < WIDTH_QWORD) {
            uint64_t prod = a * b;
            res = prod & mask;
            hi  = prod >> bits;
        } else {
#if defined(__SIZEOF_INT128__)
            unsigned __int128 prod = (unsigned __int128)a * b;
            res = (uint64_t)prod;
            hi  = (uint64_t)(prod >> 64);
#else
            res = a * b; hi = 0; /* TODO: 128-bit emulation without __int128 */
#endif
        }
        bool ov = (hi != 0);
        uint64_t f = set_bit(flags_in, FLAG_CF, ov);
        f = set_bit(f, FLAG_OF, ov);
        out.result = res; out.rflags = f;
        break;
    }
    case OP_IMUL: { /* signed (one-operand-style overflow detection) */
        int64_t sa = (int64_t)sign_extend(a, width);
        int64_t sb_ = (int64_t)sign_extend(b, width);
        int64_t prod = sa * sb_;             /* low 64 bits of product */
        uint64_t res = (uint64_t)prod & mask;
        bool ov = (sign_extend(res, width) != (uint64_t)prod);
        uint64_t f = set_bit(flags_in, FLAG_CF, ov);
        f = set_bit(f, FLAG_OF, ov);
        out.result = res; out.rflags = f;
        break;
    }
    case OP_DIV: {
        if (b == 0) { out.status = EMU_ERR_DIV_ZERO; break; }
        out.result = (a / b) & mask;          /* remainder handled in cpu.c   */
        /* DIV leaves the arithmetic flags undefined; preserve incoming. */
        out.rflags = flags_in;
        break;
    }
    case OP_IDIV: {
        if (b == 0) { out.status = EMU_ERR_DIV_ZERO; break; }
        int64_t sa = (int64_t)sign_extend(a, width);
        int64_t sb_ = (int64_t)sign_extend(b, width);
        out.result = (uint64_t)(sa / sb_) & mask;
        out.rflags = flags_in;
        break;
    }

    default:
        /* Non-ALU opcode handed to the ALU: pass through unchanged. */
        out.result = a;
        out.rflags = flags_in;
        break;
    }

    return out;
}
