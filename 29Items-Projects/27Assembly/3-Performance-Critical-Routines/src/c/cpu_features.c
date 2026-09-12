/*
 * cpu_features.c — runtime CPUID-based feature detection.
 *
 * Uses GCC/Clang __builtin_cpu_supports for the feature bits and the extended
 * CPUID leaves for the brand string. Detection is cached. The lazy init is not
 * atomic: perflib_init() is documented to be called once before concurrent use.
 */
#include "perflib/cpu_features.h"
#include <string.h>

#if defined(__GNUC__) || defined(__clang__)
#  include <cpuid.h>
#endif

static perflib_cpu_features_t g_feat;
static int g_detected;

static void detect(void)
{
    perflib_cpu_features_t f;
    memset(&f, 0, sizeof f);
    f.sse2 = 1; /* guaranteed by the x86-64 baseline */

#if defined(__GNUC__) || defined(__clang__)
    __builtin_cpu_init();
    f.avx     = __builtin_cpu_supports("avx");
    f.avx2    = __builtin_cpu_supports("avx2");
    f.fma     = __builtin_cpu_supports("fma");
    f.bmi1    = __builtin_cpu_supports("bmi");
    f.bmi2    = __builtin_cpu_supports("bmi2");
    f.avx512f = __builtin_cpu_supports("avx512f");

    /* Brand string lives in extended leaves 0x80000002..0x80000004 (48 bytes). */
    {
        unsigned int r[4];
        char *p = f.brand;
        unsigned int leaf;
        for (leaf = 0x80000002u; leaf <= 0x80000004u; ++leaf) {
            if (__get_cpuid(leaf, &r[0], &r[1], &r[2], &r[3])) {
                memcpy(p, r, sizeof r);
                p += sizeof r;
            }
        }
        f.brand[sizeof f.brand - 1] = '\0';
    }
#else
    /* TODO(win/msvc): implement via __cpuid/__cpuidex from <intrin.h>. */
    strncpy(f.brand, "unknown-cpu", sizeof f.brand - 1);
#endif

    g_feat = f;
    g_detected = 1;
}

void perflib_detect_cpu(perflib_cpu_features_t *out)
{
    if (!g_detected) detect();
    if (out) *out = g_feat;
}

int perflib_has_avx2(void)    { if (!g_detected) detect(); return g_feat.avx2; }
int perflib_has_fma(void)     { if (!g_detected) detect(); return g_feat.fma; }
int perflib_has_avx512f(void) { if (!g_detected) detect(); return g_feat.avx512f; }
