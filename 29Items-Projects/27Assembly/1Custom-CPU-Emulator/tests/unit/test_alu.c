/*
 * test_alu.c — Golden truth tables for ALU results and condition flags.
 * This is the highest-value test in the project: flag correctness is everything.
 */
#include "test_framework.h"
#include "core/alu.h"

#define HAS(f, m) (((f) & (uint64_t)(m)) != 0)

int main(void) {
    TEST_SUITE("alu");

    /* --- ADD --- */
    alu_result_t r = alu_execute(OP_ADD, 5, 7, WIDTH_DWORD, 0);
    CHECK_EQ_U64(r.result, 12);
    CHECK_FALSE(HAS(r.rflags, FLAG_ZF));
    CHECK_FALSE(HAS(r.rflags, FLAG_CF));
    CHECK_FALSE(HAS(r.rflags, FLAG_OF));

    /* unsigned carry + zero wrap */
    r = alu_execute(OP_ADD, 0xFFFFFFFFu, 1, WIDTH_DWORD, 0);
    CHECK_EQ_U64(r.result, 0);
    CHECK_TRUE(HAS(r.rflags, FLAG_CF));
    CHECK_TRUE(HAS(r.rflags, FLAG_ZF));

    /* signed overflow: INT32_MAX + 1 */
    r = alu_execute(OP_ADD, 0x7FFFFFFFu, 1, WIDTH_DWORD, 0);
    CHECK_EQ_U64(r.result, 0x80000000u);
    CHECK_TRUE(HAS(r.rflags, FLAG_OF));
    CHECK_TRUE(HAS(r.rflags, FLAG_SF));
    CHECK_FALSE(HAS(r.rflags, FLAG_CF));

    /* --- SUB / CMP --- */
    r = alu_execute(OP_SUB, 5, 7, WIDTH_DWORD, 0);
    CHECK_EQ_U64(r.result, 0xFFFFFFFEu);
    CHECK_TRUE(HAS(r.rflags, FLAG_CF));  /* borrow */
    CHECK_TRUE(HAS(r.rflags, FLAG_SF));

    r = alu_execute(OP_CMP, 9, 9, WIDTH_DWORD, 0);
    CHECK_EQ_U64(r.result, 9);           /* CMP discards result */
    CHECK_TRUE(HAS(r.rflags, FLAG_ZF));
    CHECK_FALSE(HAS(r.rflags, FLAG_CF));

    /* --- logic clears CF/OF --- */
    r = alu_execute(OP_AND, 0xF0, 0x0F, WIDTH_BYTE, FLAG_CF | FLAG_OF);
    CHECK_EQ_U64(r.result, 0);
    CHECK_TRUE(HAS(r.rflags, FLAG_ZF));
    CHECK_FALSE(HAS(r.rflags, FLAG_CF));
    CHECK_FALSE(HAS(r.rflags, FLAG_OF));

    r = alu_execute(OP_XOR, 0xABCD, 0xABCD, WIDTH_WORD, 0);
    CHECK_EQ_U64(r.result, 0);
    CHECK_TRUE(HAS(r.rflags, FLAG_ZF));

    /* --- INC preserves CF (unlike ADD) --- */
    r = alu_execute(OP_INC, 1, 0, WIDTH_DWORD, FLAG_CF);
    CHECK_EQ_U64(r.result, 2);
    CHECK_TRUE(HAS(r.rflags, FLAG_CF));  /* carry untouched */

    /* --- shifts --- */
    r = alu_execute(OP_SHL, 0x80000000u, 1, WIDTH_DWORD, 0);
    CHECK_EQ_U64(r.result, 0);
    CHECK_TRUE(HAS(r.rflags, FLAG_CF));  /* bit shifted out of MSB */

    r = alu_execute(OP_SHR, 1, 1, WIDTH_DWORD, 0);
    CHECK_EQ_U64(r.result, 0);
    CHECK_TRUE(HAS(r.rflags, FLAG_CF));

    r = alu_execute(OP_SAR, 0xFFFFFFFFu, 1, WIDTH_DWORD, 0);
    CHECK_EQ_U64(r.result, 0xFFFFFFFFu); /* arithmetic: sign preserved */

    /* --- carry-chain & unary ops --- */
    r = alu_execute(OP_ADC, 0xFFFFFFFFu, 0, WIDTH_DWORD, FLAG_CF); /* +carry-in wraps */
    CHECK_EQ_U64(r.result, 0);
    CHECK_TRUE(HAS(r.rflags, FLAG_CF));

    r = alu_execute(OP_SBB, 5, 5, WIDTH_DWORD, FLAG_CF);           /* 5 - 5 - 1 = -1 */
    CHECK_EQ_U64(r.result, 0xFFFFFFFFu);
    CHECK_TRUE(HAS(r.rflags, FLAG_CF));

    r = alu_execute(OP_DEC, 1, 0, WIDTH_DWORD, 0);
    CHECK_EQ_U64(r.result, 0);
    CHECK_TRUE(HAS(r.rflags, FLAG_ZF));

    r = alu_execute(OP_NEG, 1, 0, WIDTH_DWORD, 0);
    CHECK_EQ_U64(r.result, 0xFFFFFFFFu);
    CHECK_TRUE(HAS(r.rflags, FLAG_CF));   /* NEG sets CF for a non-zero operand */

    r = alu_execute(OP_OR, 0xF0, 0x0F, WIDTH_BYTE, 0);
    CHECK_EQ_U64(r.result, 0xFF);
    CHECK_FALSE(HAS(r.rflags, FLAG_ZF));

    r = alu_execute(OP_NOT, 0x00, 0, WIDTH_BYTE, 0);
    CHECK_EQ_U64(r.result, 0xFF);         /* NOT leaves flags untouched */

    /* --- multiply --- */
    r = alu_execute(OP_MUL, 6, 7, WIDTH_DWORD, 0);
    CHECK_EQ_U64(r.result, 42);
    CHECK_FALSE(HAS(r.rflags, FLAG_CF));  /* fits → CF/OF clear */

    r = alu_execute(OP_MUL, 0x10000, 0x10000, WIDTH_DWORD, 0);
    CHECK_TRUE(HAS(r.rflags, FLAG_CF));   /* product overflows 32 bits */

    r = alu_execute(OP_IMUL, (uint64_t)(-3) & 0xFFFFFFFFu, 4, WIDTH_DWORD, 0);
    CHECK_EQ_U64(r.result, (uint64_t)(-12) & 0xFFFFFFFFu);

    /* --- helpers --- */
    CHECK_EQ_U64(alu_width_mask(WIDTH_BYTE), 0xFFu);
    CHECK_EQ_U64(alu_width_mask(WIDTH_DWORD), 0xFFFFFFFFu);
    CHECK_TRUE(alu_parity8(0x03));   /* 2 bits set → even parity */
    CHECK_FALSE(alu_parity8(0x01));  /* 1 bit set  → odd parity  */

    /* --- divide-by-zero is reported, not crashed --- */
    r = alu_execute(OP_DIV, 10, 0, WIDTH_DWORD, 0);
    CHECK_TRUE(r.status == EMU_ERR_DIV_ZERO);

    return test_report();
}
