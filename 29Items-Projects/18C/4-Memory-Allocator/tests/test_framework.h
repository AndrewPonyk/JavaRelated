/**
 * @file test_framework.h
 * @brief Tiny, dependency-free unit-test harness.
 *
 * Each test is an `int test_xxx(void)` returning 0 on success, non-zero on
 * failure. A test executable wires them up in main() with RUN_TEST and ends
 * with `return TEST_SUMMARY();`.
 */
#ifndef TEST_FRAMEWORK_H
#define TEST_FRAMEWORK_H

#include <stdio.h>

static int g_tests_run    = 0;
static int g_tests_failed = 0;

#define RUN_TEST(fn)                                  \
    do {                                              \
        printf("---- %s\n", #fn);                     \
        g_tests_run++;                                \
        if ((fn)() != 0) {                            \
            g_tests_failed++;                         \
            printf("FAIL: %s\n", #fn);                \
        } else {                                      \
            printf("PASS: %s\n", #fn);                \
        }                                             \
    } while (0)

#define ASSERT_TRUE(cond)                             \
    do {                                              \
        if (!(cond)) {                                \
            printf("  assert failed: %s (%s:%d)\n",   \
                   #cond, __FILE__, __LINE__);        \
            return 1;                                 \
        }                                             \
    } while (0)

#define ASSERT_EQ(a, b)    ASSERT_TRUE((a) == (b))
#define ASSERT_NE(a, b)    ASSERT_TRUE((a) != (b))
#define ASSERT_NOT_NULL(p) ASSERT_TRUE((p) != NULL)
#define ASSERT_NULL(p)     ASSERT_TRUE((p) == NULL)

#define TEST_SUMMARY()                                       \
    (printf("\n%d run, %d failed\n", g_tests_run, g_tests_failed), \
     g_tests_failed == 0 ? 0 : 1)

#endif /* TEST_FRAMEWORK_H */
