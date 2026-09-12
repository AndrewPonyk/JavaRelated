/* =============================================================================
 *  test_string.c  --  Host unit tests for the freestanding libc subset
 *
 *  Compiled with kernel/lib/string.c, kernel/lib/itoa.c, and kernel/mm/memory.c
 *  (for memcpy/memset/memmove). See the Makefile `test` target.
 * ===========================================================================*/
#include "test_framework.h"
#include "../kernel/lib/string.h"
#include "../kernel/include/memory.h"

TEST(strlen_counts_chars) {
    ASSERT_EQ_INT(0, strlen(""));
    ASSERT_EQ_INT(5, strlen("hello"));
}

TEST(strcmp_orders_correctly) {
    ASSERT_EQ_INT(0, strcmp("abc", "abc"));
    ASSERT_TRUE(strcmp("abc", "abd") < 0);
    ASSERT_TRUE(strcmp("abd", "abc") > 0);
}

TEST(strncmp_respects_length) {
    ASSERT_EQ_INT(0, strncmp("abcXYZ", "abcDEF", 3));
    ASSERT_TRUE(strncmp("abcXYZ", "abcDEF", 4) != 0);
}

TEST(strcpy_and_strncpy) {
    char buf[16];
    strcpy(buf, "kernel");
    ASSERT_STR_EQ("kernel", buf);

    char nbuf[8];
    strncpy(nbuf, "abcdefghij", 8);            /* truncates, no NUL-overflow */
    ASSERT_EQ_INT('a', nbuf[0]);
    ASSERT_EQ_INT('h', nbuf[7]);
}

TEST(itoa_decimal) {
    char buf[12];
    ASSERT_STR_EQ("0",      itoa(0, buf, 10));
    ASSERT_STR_EQ("42",     itoa(42, buf, 10));
    ASSERT_STR_EQ("65535",  itoa(65535, buf, 10));
}

TEST(itoa_hex) {
    char buf[12];
    ASSERT_STR_EQ("0",    itoa(0, buf, 16));
    ASSERT_STR_EQ("ff",   itoa(255, buf, 16));
    ASSERT_STR_EQ("b8000", itoa(0xB8000, buf, 16));
}

TEST(memset_fills) {
    unsigned char buf[8];
    memset(buf, 0xAB, sizeof(buf));
    for (int i = 0; i < 8; i++) ASSERT_EQ_INT(0xAB, buf[i]);
}

TEST(memcpy_copies) {
    char src[] = "payload";
    char dst[8] = {0};
    memcpy(dst, src, sizeof(src));
    ASSERT_STR_EQ("payload", dst);
}

TEST(memmove_handles_overlap) {
    char buf[16] = "0123456789";
    /* Shift "123456789" one position right (overlapping, dst > src). */
    memmove(buf + 1, buf, 9);
    buf[10] = '\0';
    ASSERT_STR_EQ("0012345678", buf);
}

int main(void) {
    printf("== string / mem tests ==\n");
    RUN_TEST(strlen_counts_chars);
    RUN_TEST(strcmp_orders_correctly);
    RUN_TEST(strncmp_respects_length);
    RUN_TEST(strcpy_and_strncpy);
    RUN_TEST(itoa_decimal);
    RUN_TEST(itoa_hex);
    RUN_TEST(memset_fills);
    RUN_TEST(memcpy_copies);
    RUN_TEST(memmove_handles_overlap);
    TEST_SUMMARY();
}
