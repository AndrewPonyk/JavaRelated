/*
 * test_decoder.c — bytes → instruction_t for the supported subset.
 */
#include "test_framework.h"
#include "core/decoder.h"
#include "core/memory.h"

static void put(memory_t *m, uint64_t addr, const uint8_t *bytes, size_t n) {
    mem_write(m, addr, bytes, n);
}

int main(void) {
    TEST_SUITE("decoder");

    memory_t m;
    mem_init(&m, 256);
    instruction_t insn;

    /* NOP (0x90) */
    put(&m, 0, (const uint8_t[]){0x90}, 1);
    CHECK_TRUE(decode_instruction(&m, 0, &insn) == EMU_OK);
    CHECK_TRUE(insn.op == OP_NOP);
    CHECK_EQ_U64(insn.length, 1);

    /* MOV eax, 5  (B8 05 00 00 00) */
    put(&m, 8, (const uint8_t[]){0xB8, 0x05, 0x00, 0x00, 0x00}, 5);
    CHECK_TRUE(decode_instruction(&m, 8, &insn) == EMU_OK);
    CHECK_TRUE(insn.op == OP_MOV);
    CHECK_TRUE(insn.dst.kind == OPERAND_REG && insn.dst.reg == REG_RAX);
    CHECK_TRUE(insn.src.kind == OPERAND_IMM);
    CHECK_EQ_U64(insn.src.imm, 5);
    CHECK_EQ_U64(insn.length, 5);

    /* ADD eax, ecx  (01 C8) — 0x01 /r: dst=r/m(EAX), src=reg(ECX) */
    put(&m, 16, (const uint8_t[]){0x01, 0xC8}, 2);
    CHECK_TRUE(decode_instruction(&m, 16, &insn) == EMU_OK);
    CHECK_TRUE(insn.op == OP_ADD);
    CHECK_TRUE(insn.dst.kind == OPERAND_REG && insn.dst.reg == REG_RAX);
    CHECK_TRUE(insn.src.kind == OPERAND_REG && insn.src.reg == REG_RCX);
    CHECK_EQ_U64(insn.length, 2);

    /* INT 0x80 (CD 80) */
    put(&m, 24, (const uint8_t[]){0xCD, 0x80}, 2);
    CHECK_TRUE(decode_instruction(&m, 24, &insn) == EMU_OK);
    CHECK_TRUE(insn.op == OP_INT);
    CHECK_EQ_U64(insn.dst.imm, 0x80);

    /* HLT (F4) */
    put(&m, 32, (const uint8_t[]){0xF4}, 1);
    CHECK_TRUE(decode_instruction(&m, 32, &insn) == EMU_OK);
    CHECK_TRUE(insn.op == OP_HLT);

    /* PUSH rax (0x50) / POP rbx (0x5B) */
    put(&m, 34, (const uint8_t[]){0x50}, 1);
    CHECK_TRUE(decode_instruction(&m, 34, &insn) == EMU_OK);
    CHECK_TRUE(insn.op == OP_PUSH && insn.dst.reg == REG_RAX);
    put(&m, 35, (const uint8_t[]){0x5B}, 1);
    CHECK_TRUE(decode_instruction(&m, 35, &insn) == EMU_OK);
    CHECK_TRUE(insn.op == OP_POP && insn.dst.reg == REG_RBX);

    /* REX.W MOV rax, imm64 (48 B8 + 8 bytes) → 64-bit operand */
    put(&m, 40, (const uint8_t[]){0x48, 0xB8, 0x88, 0x77, 0x66, 0x55,
                                  0x44, 0x33, 0x22, 0x11}, 10);
    CHECK_TRUE(decode_instruction(&m, 40, &insn) == EMU_OK);
    CHECK_TRUE(insn.op == OP_MOV && insn.rex_w);
    CHECK_EQ_U64(insn.src.imm, 0x1122334455667788ull);
    CHECK_EQ_U64(insn.length, 10);

    /* Unsupported opcode → clean #UD signal. */
    put(&m, 60, (const uint8_t[]){0x0F}, 1);
    CHECK_TRUE(decode_instruction(&m, 60, &insn) == EMU_ERR_INVALID_OPCODE);

    /* Truncated immediate at the very end of memory → decode error. */
    put(&m, 255, (const uint8_t[]){0xB8}, 1);
    CHECK_TRUE(decode_instruction(&m, 255, &insn) == EMU_ERR_DECODE);

    /* Disassembly smoke test. */
    put(&m, 64, (const uint8_t[]){0x01, 0xC8}, 2);
    decode_instruction(&m, 64, &insn);
    char buf[64];
    CHECK_TRUE(disasm_format(&insn, buf, sizeof buf) > 0);

    mem_free(&m);
    return test_report();
}
