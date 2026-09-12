/* test_util.h — minimal assertion macros for the CTest executables.
 * No third-party dependency: each test is a tiny main() returning non-zero on
 * the first failure so CTest reports it. */
#ifndef PPMON_TEST_UTIL_H
#define PPMON_TEST_UTIL_H

#include <stdio.h>
#include <string.h>
#include <math.h>

static int g_failures = 0;

#define CHECK(cond)                                                                           \
    do {                                                                                      \
        if (!(cond)) {                                                                        \
            fprintf(stderr, "  FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond);                 \
            ++g_failures;                                                                     \
        }                                                                                     \
    } while (0)

#define CHECK_EQ_INT(a, b)                                                                    \
    do {                                                                                      \
        long long _a = (long long)(a), _b = (long long)(b);                                   \
        if (_a != _b) {                                                                       \
            fprintf(stderr, "  FAIL %s:%d: %s (%lld) != %s (%lld)\n", __FILE__, __LINE__, #a, \
                    _a, #b, _b);                                                              \
            ++g_failures;                                                                     \
        }                                                                                     \
    } while (0)

#define CHECK_EQ_STR(a, b)                                                                    \
    do {                                                                                      \
        if (strcmp((a), (b)) != 0) {                                                          \
            fprintf(stderr, "  FAIL %s:%d: \"%s\" != \"%s\"\n", __FILE__, __LINE__, (a),      \
                    (b));                                                                     \
            ++g_failures;                                                                     \
        }                                                                                     \
    } while (0)

#define CHECK_NEAR(a, b, eps)                                                                 \
    do {                                                                                      \
        if (fabs((double)(a) - (double)(b)) > (eps)) {                                        \
            fprintf(stderr, "  FAIL %s:%d: %g !~ %g\n", __FILE__, __LINE__, (double)(a),      \
                    (double)(b));                                                             \
            ++g_failures;                                                                     \
        }                                                                                     \
    } while (0)

#define TEST_MAIN_RETURN() return g_failures == 0 ? 0 : 1

#endif /* PPMON_TEST_UTIL_H */
