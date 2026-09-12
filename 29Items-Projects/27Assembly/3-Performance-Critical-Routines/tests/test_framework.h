/*
 * test_framework.h — header-only assertion framework (zero dependencies).
 * Upgrade path if richer reporting is wanted: Unity / greatest / Criterion.
 */
#ifndef PERFLIB_TEST_FRAMEWORK_H
#define PERFLIB_TEST_FRAMEWORK_H

#include <stdio.h>
#include <math.h>

extern int g_tests_run;
extern int g_tests_failed;

#define CHECK(cond)                                                            \
    do {                                                                       \
        g_tests_run++;                                                         \
        if (!(cond)) {                                                         \
            g_tests_failed++;                                                  \
            fprintf(stderr, "  FAIL %s:%d: CHECK(%s)\n",                       \
                    __FILE__, __LINE__, #cond);                               \
        }                                                                      \
    } while (0)

#define CHECK_EQ_INT(a, b)                                                     \
    do {                                                                       \
        long _a = (long)(a), _b = (long)(b);                                   \
        g_tests_run++;                                                         \
        if (_a != _b) {                                                        \
            g_tests_failed++;                                                  \
            fprintf(stderr, "  FAIL %s:%d: %s (%ld) != %s (%ld)\n",            \
                    __FILE__, __LINE__, #a, _a, #b, _b);                       \
        }                                                                      \
    } while (0)

/* Relative+absolute tolerance compare (correct unit for FP/FMA reductions). */
static inline int perflib_close(float a, float b, float rel, float abs_floor)
{
    float diff = fabsf(a - b);
    float mag  = fabsf(b);
    if (mag < 1.0f) mag = 1.0f;
    float tol = rel * mag;
    if (tol < abs_floor) tol = abs_floor;
    return diff <= tol;
}

#define CHECK_CLOSE(a, b, rel, abs_floor)                                      \
    do {                                                                       \
        g_tests_run++;                                                         \
        if (!perflib_close((a), (b), (rel), (abs_floor))) {                    \
            g_tests_failed++;                                                  \
            fprintf(stderr, "  FAIL %s:%d: %s=%g not ~= %s=%g\n",              \
                    __FILE__, __LINE__, #a, (double)(a), #b, (double)(b));     \
        }                                                                      \
    } while (0)

#define RUN_SUITE(fn)                                                          \
    do { fprintf(stderr, "[suite] %s\n", #fn); fn(); } while (0)

#endif /* PERFLIB_TEST_FRAMEWORK_H */
