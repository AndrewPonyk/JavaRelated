/*
 * test_registers.c — Sub-register aliasing rules (a real x86-64 footgun).
 */
#include "test_framework.h"
#include "core/registers.h"

int main(void) {
    TEST_SUITE("registers");

    register_file_t rf;
    regfile_reset(&rf);

    /* All zero at reset, except RFLAGS reserved bit 1. */
    CHECK_EQ_U64(reg_read(&rf, REG_RAX, WIDTH_QWORD), 0);
    CHECK_EQ_U64(rf.rflags, 0x2u);

    /* 8-bit write preserves the upper 56 bits. */
    reg_write(&rf, REG_RAX, WIDTH_QWORD, 0x1122334455667788ull);
    reg_write(&rf, REG_RAX, WIDTH_BYTE, 0xFF);
    CHECK_EQ_U64(reg_read(&rf, REG_RAX, WIDTH_QWORD), 0x11223344556677FFull);
    CHECK_EQ_U64(reg_read(&rf, REG_RAX, WIDTH_BYTE), 0xFF);

    /* 16-bit write preserves the upper 48 bits. */
    reg_write(&rf, REG_RAX, WIDTH_WORD, 0xBEEF);
    CHECK_EQ_U64(reg_read(&rf, REG_RAX, WIDTH_QWORD), 0x112233445566BEEFull);

    /* 32-bit write ZERO-EXTENDS into the full 64-bit register (key x86-64 rule). */
    reg_write(&rf, REG_RAX, WIDTH_QWORD, 0xFFFFFFFFFFFFFFFFull);
    reg_write(&rf, REG_RAX, WIDTH_DWORD, 0x12345678u);
    CHECK_EQ_U64(reg_read(&rf, REG_RAX, WIDTH_QWORD), 0x0000000012345678ull);

    /* Flag get/set round-trips. */
    flag_set(&rf, FLAG_ZF, true);
    CHECK_TRUE(flag_get(&rf, FLAG_ZF));
    flag_set(&rf, FLAG_ZF, false);
    CHECK_FALSE(flag_get(&rf, FLAG_ZF));

    /* Register naming by width. */
    CHECK_TRUE(reg_name(REG_RAX, WIDTH_QWORD)[0] == 'r');
    CHECK_TRUE(reg_name(REG_RAX, WIDTH_DWORD)[0] == 'e');

    return test_report();
}
