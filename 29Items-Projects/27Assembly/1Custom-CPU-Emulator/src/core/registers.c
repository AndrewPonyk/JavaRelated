/*
 * registers.c — Register file with x86-64 sub-register write semantics.
 */
#include "core/registers.h"

#include <string.h>

void regfile_reset(register_file_t *rf) {
    if (rf == NULL) {
        return;
    }
    memset(rf, 0, sizeof *rf);
    /* RFLAGS bit 1 is reserved and always reads as 1 on real hardware. */
    rf->rflags = 0x2u;
}

uint64_t reg_read(const register_file_t *rf, reg_id_t id, uint8_t width) {
    if (rf == NULL || id >= REG_COUNT) {
        return 0;
    }
    uint64_t v = rf->gpr[id];
    switch (width) {
        case WIDTH_BYTE:  return v & 0xFFu;
        case WIDTH_WORD:  return v & 0xFFFFu;
        case WIDTH_DWORD: return v & 0xFFFFFFFFu;
        case WIDTH_QWORD: return v;
        default:          return v;
    }
}

void reg_write(register_file_t *rf, reg_id_t id, uint8_t width, uint64_t value) {
    if (rf == NULL || id >= REG_COUNT) {
        return;
    }
    switch (width) {
        case WIDTH_BYTE: /* preserve upper 56 bits */
            rf->gpr[id] = (rf->gpr[id] & ~0xFFull) | (value & 0xFFull);
            break;
        case WIDTH_WORD: /* preserve upper 48 bits */
            rf->gpr[id] = (rf->gpr[id] & ~0xFFFFull) | (value & 0xFFFFull);
            break;
        case WIDTH_DWORD: /* 32-bit writes ZERO-EXTEND to 64 bits (x86-64 rule) */
            rf->gpr[id] = value & 0xFFFFFFFFull;
            break;
        case WIDTH_QWORD:
        default:
            rf->gpr[id] = value;
            break;
    }
}

bool flag_get(const register_file_t *rf, flag_mask_t flag) {
    return rf != NULL && (rf->rflags & (uint64_t)flag) != 0;
}

void flag_set(register_file_t *rf, flag_mask_t flag, bool value) {
    if (rf == NULL) {
        return;
    }
    if (value) {
        rf->rflags |= (uint64_t)flag;
    } else {
        rf->rflags &= ~(uint64_t)flag;
    }
}

const char *reg_name(reg_id_t id, uint8_t width) {
    static const char *const q[REG_COUNT] = {
        "rax","rcx","rdx","rbx","rsp","rbp","rsi","rdi",
        "r8","r9","r10","r11","r12","r13","r14","r15"
    };
    static const char *const d[REG_COUNT] = {
        "eax","ecx","edx","ebx","esp","ebp","esi","edi",
        "r8d","r9d","r10d","r11d","r12d","r13d","r14d","r15d"
    };
    static const char *const w[REG_COUNT] = {
        "ax","cx","dx","bx","sp","bp","si","di",
        "r8w","r9w","r10w","r11w","r12w","r13w","r14w","r15w"
    };
    static const char *const b[REG_COUNT] = {
        "al","cl","dl","bl","spl","bpl","sil","dil",
        "r8b","r9b","r10b","r11b","r12b","r13b","r14b","r15b"
    };
    if (id >= REG_COUNT) {
        return "?";
    }
    switch (width) {
        case WIDTH_BYTE:  return b[id];
        case WIDTH_WORD:  return w[id];
        case WIDTH_DWORD: return d[id];
        case WIDTH_QWORD:
        default:          return q[id];
    }
}
