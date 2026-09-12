/* SPDX-License-Identifier: MIT
 *
 * tests/test_common.h — a zero-dependency micro test framework.
 *
 * Each test file includes this header, defines `void test_*(void)` functions
 * using the ASSERT_* macros, and lists them in a main() via RUN_TEST(). If the
 * suite grows, swap this for Unity (tests/unity/) without touching test bodies.
 *
 * Usage:
 *     #include "test_common.h"
 *     static void test_foo(void) { ASSERT_EQ_INT(2, 1 + 1); }
 *     int main(void) {
 *         RUN_TEST(test_foo);
 *         return test_summary("decode");
 *     }
 */
#ifndef NPA_TEST_COMMON_H
#define NPA_TEST_COMMON_H

#include <stdio.h>
#include <string.h>

static int t_checks = 0;
static int t_fails  = 0;
static const char *t_current = "";

#define RUN_TEST(fn)                                                          \
    do {                                                                      \
        t_current = #fn;                                                      \
        int before = t_fails;                                                 \
        fn();                                                                 \
        printf("  [%s] %s\n", (t_fails == before) ? "PASS" : "FAIL", #fn);    \
    } while (0)

#define ASSERT_TRUE(cond)                                                     \
    do {                                                                      \
        t_checks++;                                                           \
        if (!(cond)) {                                                        \
            t_fails++;                                                        \
            printf("    ASSERT_TRUE failed: %s (%s:%d) in %s\n",              \
                   #cond, __FILE__, __LINE__, t_current);                     \
        }                                                                     \
    } while (0)

#define ASSERT_FALSE(cond) ASSERT_TRUE(!(cond))

#define ASSERT_EQ_INT(expected, actual)                                       \
    do {                                                                      \
        t_checks++;                                                           \
        long long _e = (long long)(expected), _a = (long long)(actual);      \
        if (_e != _a) {                                                       \
            t_fails++;                                                        \
            printf("    ASSERT_EQ_INT failed: expected %lld, got %lld "       \
                   "(%s:%d) in %s\n", _e, _a, __FILE__, __LINE__, t_current); \
        }                                                                     \
    } while (0)

#define ASSERT_EQ_UINT(expected, actual)                                      \
    do {                                                                      \
        t_checks++;                                                           \
        unsigned long long _e = (unsigned long long)(expected);              \
        unsigned long long _a = (unsigned long long)(actual);                \
        if (_e != _a) {                                                       \
            t_fails++;                                                        \
            printf("    ASSERT_EQ_UINT failed: expected %llu, got %llu "      \
                   "(%s:%d) in %s\n", _e, _a, __FILE__, __LINE__, t_current); \
        }                                                                     \
    } while (0)

#define ASSERT_STR_EQ(expected, actual)                                       \
    do {                                                                      \
        t_checks++;                                                           \
        if (strcmp((expected), (actual)) != 0) {                             \
            t_fails++;                                                        \
            printf("    ASSERT_STR_EQ failed: expected \"%s\", got \"%s\" "   \
                   "(%s:%d) in %s\n", (expected), (actual),                   \
                   __FILE__, __LINE__, t_current);                            \
        }                                                                     \
    } while (0)

/* Print summary; return 0 if all passed, 1 otherwise (for CI exit code). */
static inline int test_summary(const char *suite) {
    printf("\n%s: %d checks, %d failures\n", suite, t_checks, t_fails);
    return t_fails == 0 ? 0 : 1;
}

#endif /* NPA_TEST_COMMON_H */
