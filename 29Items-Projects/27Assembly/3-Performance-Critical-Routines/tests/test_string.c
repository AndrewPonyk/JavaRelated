/* test_string.c — AVX2 string scans vs C reference + libc, incl. page safety. */
#include "perflib/perflib.h"
#include "test_framework.h"
#include <stdlib.h>
#include <string.h>

#if defined(__unix__) || defined(__APPLE__)
#  include <sys/mman.h>
#  include <unistd.h>
#endif

static void test_strlen_basic(void)
{
    const char *cases[] = {
        "", "a", "abcdefg",                          /* 0,1,7 */
        "abcdefgh", "abcdefghi",                      /* 8,9   */
        "0123456789012345678901234567890",            /* 31    */
        "01234567890123456789012345678901",           /* 32    */
        "012345678901234567890123456789012",          /* 33    */
        "the quick brown fox jumps over the lazy dog"
    };
    for (size_t i = 0; i < sizeof cases / sizeof *cases; ++i) {
        size_t rc = perflib_strlen_c(cases[i]);
        size_t ra = perflib_strlen_avx2(cases[i]);
        CHECK_EQ_INT(ra, rc);
        CHECK_EQ_INT(ra, strlen(cases[i]));
    }
}

static void test_strlen_unaligned(void)
{
    char buf[80];
    memset(buf, 'x', 79);
    buf[79] = '\0';
    for (int off = 0; off < 32; ++off) {
        size_t ra = perflib_strlen_avx2(buf + off);
        size_t rc = strlen(buf + off);
        CHECK_EQ_INT(ra, rc);
    }
}

static void test_memchr_basic(void)
{
    const char *s = "hello, simd world";
    size_t n = strlen(s);
    for (int c = 0; c < 128; ++c) {
        void *ra = perflib_memchr_avx2(s, c, n);
        void *rc = perflib_memchr_c(s, c, n);
        CHECK(ra == rc);
    }
    CHECK(perflib_memchr_avx2(s, 'z', n) == NULL);
}

#if defined(__unix__) || defined(__APPLE__)
/* Place strings ending exactly at the last byte before an inaccessible guard
 * page; a correct page-safe scanner must never fault by reading into it. */
static void test_strlen_pagecross(void)
{
    long pg = sysconf(_SC_PAGESIZE);
    char *base = mmap(NULL, (size_t)pg * 2, PROT_READ | PROT_WRITE,
                      MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (base == MAP_FAILED) { CHECK(0); return; }
    if (mprotect(base + pg, (size_t)pg, PROT_NONE) != 0) {
        CHECK(0); munmap(base, (size_t)pg * 2); return;
    }
    for (int len = 1; len <= 64; ++len) {
        char *s = base + pg - len;          /* terminator at base+pg-1 */
        memset(s, 'a', (size_t)len - 1);
        s[len - 1] = '\0';
        size_t r = perflib_strlen_avx2(s);  /* must not touch the guard page */
        CHECK_EQ_INT(r, (size_t)(len - 1));
    }
    munmap(base, (size_t)pg * 2);
}
#endif

void test_string(void)
{
    if (!perflib_has_avx2()) {
        fprintf(stderr, "  [skip] AVX2 not present at runtime\n");
        return;
    }
    test_strlen_basic();
    test_strlen_unaligned();
    test_memchr_basic();
#if defined(__unix__) || defined(__APPLE__)
    test_strlen_pagecross();
#else
    fprintf(stderr, "  [skip] page-cross test (needs POSIX mmap/mprotect)\n");
#endif
}
