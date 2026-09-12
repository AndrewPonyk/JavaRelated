/* =============================================================================
 *  test_kprintf.c  --  Host unit tests for the printf-family formatter
 *
 *  Compiled with kernel/lib/kprintf.c (pure: formats into a caller buffer; the
 *  sink is optional and unused here).
 * ===========================================================================*/
#include "test_framework.h"
#include "../kernel/include/kprintf.h"

TEST(plain_text_and_percent) {
    char b[32];
    ksnprintf(b, sizeof(b), "hi");
    ASSERT_STR_EQ("hi", b);
    ksnprintf(b, sizeof(b), "100%%");
    ASSERT_STR_EQ("100%", b);
}

TEST(signed_and_unsigned_decimal) {
    char b[32];
    ksnprintf(b, sizeof(b), "%d", 42);
    ASSERT_STR_EQ("42", b);
    ksnprintf(b, sizeof(b), "%d", -7);
    ASSERT_STR_EQ("-7", b);
    ksnprintf(b, sizeof(b), "%u", 4294967295u);
    ASSERT_STR_EQ("4294967295", b);
}

TEST(hex_upper_lower_and_width) {
    char b[32];
    ksnprintf(b, sizeof(b), "%x", 255u);
    ASSERT_STR_EQ("ff", b);
    ksnprintf(b, sizeof(b), "%X", 255u);
    ASSERT_STR_EQ("FF", b);
    ksnprintf(b, sizeof(b), "%08x", 0xABCDu);
    ASSERT_STR_EQ("0000abcd", b);
}

TEST(width_padding_and_pointer) {
    char b[32];
    ksnprintf(b, sizeof(b), "%5d", 42);
    ASSERT_STR_EQ("   42", b);
    ksnprintf(b, sizeof(b), "%p", (void *)0x1000);
    ASSERT_STR_EQ("0x00001000", b);
}

TEST(strings_chars_and_mixed) {
    char b[64];
    ksnprintf(b, sizeof(b), "%s", "world");
    ASSERT_STR_EQ("world", b);
    ksnprintf(b, sizeof(b), "%c", 'Z');
    ASSERT_STR_EQ("Z", b);
    ksnprintf(b, sizeof(b), "x=%d y=%x", 1, 0x2au);
    ASSERT_STR_EQ("x=1 y=2a", b);
}

TEST(null_string_renders_safely) {
    char b[32];
    ksnprintf(b, sizeof(b), "%s", (char *)0);
    ASSERT_STR_EQ("(null)", b);
}

TEST(returns_length_and_truncates) {
    char b[4];
    int n = ksnprintf(b, sizeof(b), "12345");  /* would-be length is 5 */
    ASSERT_EQ_INT(5, n);
    ASSERT_STR_EQ("123", b);                    /* truncated, NUL-terminated */
}

int main(void) {
    printf("== kprintf tests ==\n");
    RUN_TEST(plain_text_and_percent);
    RUN_TEST(signed_and_unsigned_decimal);
    RUN_TEST(hex_upper_lower_and_width);
    RUN_TEST(width_padding_and_pointer);
    RUN_TEST(strings_chars_and_mixed);
    RUN_TEST(null_string_renders_safely);
    RUN_TEST(returns_length_and_truncates);
    TEST_SUMMARY();
}
