/*
 * dispatch.c — binds the dispatched public symbols to the fastest legal impl.
 *
 * Function pointers default to the C reference, so every routine is correct even
 * if perflib_init() is never called. perflib_init() upgrades them to AVX2 when
 * CPUID confirms AVX2+FMA. After init the table is read-only (no data races).
 */
#include "perflib/perflib.h"
#include <stddef.h>

typedef void   (*saxpy_fn) (int, float, const float *, float *);
typedef float  (*sdot_fn)  (int, const float *, const float *);
typedef void   (*sgemm_fn) (int, int, int, const float *, int,
                            const float *, int, float *, int);
typedef size_t (*strlen_fn)(const char *);
typedef void  *(*memchr_fn)(const void *, int, size_t);

/* Defaults: portable C reference (always correct). */
static saxpy_fn  g_saxpy  = perflib_saxpy_c;
static sdot_fn   g_sdot   = perflib_sdot_c;
static sgemm_fn  g_sgemm  = perflib_sgemm_c;
static strlen_fn g_strlen = perflib_strlen_c;
static memchr_fn g_memchr = perflib_memchr_c;

static perflib_isa_t g_isa = PERFLIB_ISA_SCALAR;

perflib_isa_t perflib_init(void)
{
    perflib_cpu_features_t f;
    perflib_detect_cpu(&f);

    if (f.avx2 && f.fma) {
        g_saxpy  = perflib_saxpy_avx2;
        g_sdot   = perflib_sdot_avx2;
        g_sgemm  = perflib_sgemm_avx2;  /* N%8 fallback handled in wrapper */
        g_strlen = perflib_strlen_avx2;
        g_memchr = perflib_memchr_avx2;
        g_isa    = PERFLIB_ISA_AVX2;
    } else {
        g_isa = PERFLIB_ISA_SCALAR;     /* keep C defaults */
    }
    /* TODO: add PERFLIB_ISA_AVX512 tier once kernels exist (and gate on
     *       downclock heuristics — AVX-512 isn't always a win). */
    return g_isa;
}

perflib_isa_t perflib_active_isa(void) { return g_isa; }

const char *perflib_version_string(void)
{
    /* Static literals: no shared mutable buffer, so this is thread-safe. */
    switch (g_isa) {
    case PERFLIB_ISA_AVX2:   return "perflib " PERFLIB_VERSION_STRING " [AVX2]";
    case PERFLIB_ISA_AVX512: return "perflib " PERFLIB_VERSION_STRING " [AVX512]";
    default:                 return "perflib " PERFLIB_VERSION_STRING " [scalar]";
    }
}

/* -------- dispatched public entry points -------- */

void perflib_saxpy(int n, float a, const float *x, float *y)
{
    g_saxpy(n, a, x, y);
}

float perflib_sdot(int n, const float *x, const float *y)
{
    return g_sdot(n, x, y);
}

void perflib_sgemm(int M, int N, int K,
                   const float *A, int lda,
                   const float *B, int ldb,
                   float *C, int ldc)
{
    /* The AVX2 sgemm currently vectorizes only N % 8 == 0; route the rest to
     * the C reference so results are always correct. (See matrix_avx2.asm.) */
    if (g_sgemm == perflib_sgemm_avx2 && (N & 7) != 0) {
        perflib_sgemm_c(M, N, K, A, lda, B, ldb, C, ldc);
        return;
    }
    g_sgemm(M, N, K, A, lda, B, ldb, C, ldc);
}

size_t perflib_strlen(const char *s)
{
    return g_strlen(s);
}

void *perflib_memchr(const void *s, int c, size_t n)
{
    return g_memchr(s, c, n);
}
