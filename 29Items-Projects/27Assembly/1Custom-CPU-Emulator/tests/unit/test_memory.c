/*
 * test_memory.c — Bounds checking, little-endian width helpers, overflow safety.
 */
#include "test_framework.h"
#include "core/memory.h"

int main(void) {
    TEST_SUITE("memory");

    memory_t m;
    CHECK_TRUE(mem_init(&m, 256) == EMU_OK);

    /* Little-endian store/load round-trip. */
    CHECK_TRUE(mem_write_width(&m, 0x10, WIDTH_DWORD, 0xDEADBEEFu) == EMU_OK);
    uint64_t v = 0;
    CHECK_TRUE(mem_read_width(&m, 0x10, WIDTH_DWORD, &v) == EMU_OK);
    CHECK_EQ_U64(v, 0xDEADBEEFu);

    /* Byte order is little-endian: lowest address holds the least-significant byte. */
    uint64_t b0 = 0, b3 = 0;
    mem_read_width(&m, 0x10, WIDTH_BYTE, &b0);
    mem_read_width(&m, 0x13, WIDTH_BYTE, &b3);
    CHECK_EQ_U64(b0, 0xEF);
    CHECK_EQ_U64(b3, 0xDE);

    /* In-bounds edge: last 8 bytes are writable. */
    CHECK_TRUE(mem_write_width(&m, 256 - 8, WIDTH_QWORD, 0x1122334455667788ull) == EMU_OK);

    /* Out-of-bounds is rejected, not crashed. */
    CHECK_TRUE(mem_write_width(&m, 256 - 4, WIDTH_QWORD, 0) == EMU_ERR_MEM_BOUNDS);
    CHECK_TRUE(mem_read_width(&m, 256, WIDTH_BYTE, &v) == EMU_ERR_MEM_BOUNDS);

    /* Overflow safety: a huge address must not wrap past the bound check. */
    CHECK_TRUE(mem_read_width(&m, 0xFFFFFFFFFFFFFFFFull, WIDTH_BYTE, &v) == EMU_ERR_MEM_BOUNDS);

    /* NULL handling. */
    CHECK_TRUE(mem_read_width(&m, 0, WIDTH_BYTE, NULL) == EMU_ERR_NULL);

    mem_free(&m);
    return test_report();
}
