/*
 * test_framework.h — Zero-dependency assertion harness.
 *
 * Each test file is a standalone program: it includes this header, runs CHECK_*
 * macros inside main(), and returns test_report(). No external test framework is
 * needed, which keeps the build trivial and portable (see docs/TECH-NOTES.md §3.2).
 */
#ifndef CPUEMU_TEST_FRAMEWORK_H
#define CPUEMU_TEST_FRAMEWORK_H

#include <stdio.h>
#include <stdint.h>

static int g_checks = 0;
static int g_fails  = 0;
static const char *g_suite = "suite";

#define TEST_SUITE(name) do { g_suite = (name); printf("• %s\n", g_suite); } while (0)

#define CHECK(cond, what)                                                       \
    do {                                                                        \
        g_checks++;                                                             \
        if (!(cond)) {                                                          \
            g_fails++;                                                          \
            printf("  FAIL %s:%d: %s\n", __FILE__, __LINE__, (what));           \
        }                                                                       \
    } while (0)

#define CHECK_EQ_U64(actual, expected)                                          \
    do {                                                                        \
        g_checks++;                                                             \
        uint64_t _a = (uint64_t)(actual), _e = (uint64_t)(expected);            \
        if (_a != _e) {                                                         \
            g_fails++;                                                          \
            printf("  FAIL %s:%d: expected 0x%llx, got 0x%llx\n",               \
                   __FILE__, __LINE__,                                          \
                   (unsigned long long)_e, (unsigned long long)_a);             \
        }                                                                       \
    } while (0)

#define CHECK_TRUE(cond)  CHECK((cond), #cond " should be true")
#define CHECK_FALSE(cond) CHECK(!(cond), #cond " should be false")

static int test_report(void) {
    printf("  %d checks, %d failure(s)\n", g_checks, g_fails);
    return g_fails == 0 ? 0 : 1;
}

#endif /* CPUEMU_TEST_FRAMEWORK_H */
