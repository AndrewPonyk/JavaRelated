/* =============================================================================
 *  test_framework.h  --  Tiny header-only unit-test harness
 *
 *  Zero dependencies beyond <stdio.h> so it runs anywhere the host compiler
 *  does. Each test file defines tests with TEST(), registers assertions with
 *  the ASSERT_ macros, and ends main() with TEST_SUMMARY() which returns a
 *  non-zero exit code if anything failed (so CI gates on it).
 * ===========================================================================*/
#ifndef MINIOS_TEST_FRAMEWORK_H
#define MINIOS_TEST_FRAMEWORK_H

#include <stdio.h>

static int tf_checks_run = 0;
static int tf_checks_failed = 0;

#define TEST(name) static void name(void)

#define RUN_TEST(name)                                                      \
    do {                                                                    \
        printf("• %-32s", #name);                                           \
        int before = tf_checks_failed;                                      \
        name();                                                             \
        printf(tf_checks_failed == before ? "  PASS\n" : "  FAIL\n");       \
    } while (0)

#define ASSERT_TRUE(cond)                                                   \
    do {                                                                    \
        tf_checks_run++;                                                    \
        if (!(cond)) {                                                      \
            tf_checks_failed++;                                             \
            printf("\n    [FAIL] %s:%d  ASSERT_TRUE(%s)\n",                 \
                   __FILE__, __LINE__, #cond);                             \
        }                                                                   \
    } while (0)

#define ASSERT_EQ_INT(expected, actual)                                     \
    do {                                                                    \
        tf_checks_run++;                                                    \
        long _e = (long)(expected), _a = (long)(actual);                    \
        if (_e != _a) {                                                     \
            tf_checks_failed++;                                             \
            printf("\n    [FAIL] %s:%d  expected %ld, got %ld  (%s)\n",     \
                   __FILE__, __LINE__, _e, _a, #actual);                   \
        }                                                                   \
    } while (0)

#define ASSERT_STR_EQ(expected, actual)                                     \
    do {                                                                    \
        tf_checks_run++;                                                    \
        if (tf_streq((expected), (actual)) == 0) {                          \
            tf_checks_failed++;                                             \
            printf("\n    [FAIL] %s:%d  expected \"%s\", got \"%s\"\n",     \
                   __FILE__, __LINE__, (expected), (actual));              \
        }                                                                   \
    } while (0)

/* Used only by ASSERT_STR_EQ; mark unused so suites that don't compare strings
 * build warning-free. (GCC/Clang attribute; MSVC simply ignores the guard.) */
#if defined(__GNUC__)
__attribute__((unused))
#endif
static int tf_streq(const char *a, const char *b) {
    while (*a && (*a == *b)) { a++; b++; }
    return (*a == *b);  /* 1 if equal, 0 otherwise */
}

#define TEST_SUMMARY()                                                      \
    do {                                                                    \
        printf("\n%d checks, %d failed.\n", tf_checks_run, tf_checks_failed);\
        return tf_checks_failed == 0 ? 0 : 1;                               \
    } while (0)

#endif /* MINIOS_TEST_FRAMEWORK_H */
