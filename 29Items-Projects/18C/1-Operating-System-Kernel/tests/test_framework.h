/*
 * test_framework.h — Tiny header-only assertion harness for host unit tests.
 *
 * No external dependencies: compiles with the host gcc and the host libc.
 * Usage:
 *     TEST(name) { ... ASSERT_EQ(a, b); }
 *     int main(void) { RUN(name); return test_summary(); }
 */
#ifndef TEST_FRAMEWORK_H
#define TEST_FRAMEWORK_H

#include <stdio.h>

static int  g_tests_run;
static int  g_tests_failed;
static int  g_current_failed;

#define TEST(name) static void test_##name(void)

#define RUN(name)                                              \
    do {                                                       \
        g_current_failed = 0;                                  \
        g_tests_run++;                                         \
        test_##name();                                         \
        if (g_current_failed) {                                \
            g_tests_failed++;                                  \
            printf("  [FAIL] %s\n", #name);                    \
        } else {                                               \
            printf("  [ ok ] %s\n", #name);                    \
        }                                                      \
    } while (0)

#define ASSERT_TRUE(cond)                                      \
    do {                                                       \
        if (!(cond)) {                                         \
            g_current_failed = 1;                              \
            printf("    assert failed: %s (%s:%d)\n",          \
                   #cond, __FILE__, __LINE__);                 \
        }                                                      \
    } while (0)

#define ASSERT_EQ(a, b)   ASSERT_TRUE((a) == (b))
#define ASSERT_NE(a, b)   ASSERT_TRUE((a) != (b))
#define ASSERT_NULL(p)    ASSERT_TRUE((p) == NULL)
#define ASSERT_NOT_NULL(p) ASSERT_TRUE((p) != NULL)

static int test_summary(void)
{
    printf("\n%d tests, %d failed\n", g_tests_run, g_tests_failed);
    return g_tests_failed == 0 ? 0 : 1;
}

#endif /* TEST_FRAMEWORK_H */
