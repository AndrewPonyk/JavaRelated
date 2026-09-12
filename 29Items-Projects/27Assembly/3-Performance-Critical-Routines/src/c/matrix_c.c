/*
 * matrix_c.c — portable C reference implementations.
 *
 * These are the correctness ground truth AND the "auto-vectorized compiler
 * output" baseline that the AVX2 kernels are benchmarked against. Written in a
 * vectorizer-friendly form (contiguous inner loops) so that `-O3 -march=native`
 * produces a genuinely competitive baseline — an honest comparison.
 */
#include "perflib/matrix.h"
#include <stddef.h>

void perflib_saxpy_c(int n, float a, const float *x, float *y)
{
    for (int i = 0; i < n; ++i)
        y[i] += a * x[i];
}

float perflib_sdot_c(int n, const float *x, const float *y)
{
    float s = 0.0f;
    for (int i = 0; i < n; ++i)
        s += x[i] * y[i];
    return s;
}

/*
 * C := A*B + C, row-major.
 * ikj loop order keeps the inner loop contiguous over B and C (unit stride),
 * which both vectorizes well and reuses A[i,k] as a broadcast scalar.
 */
void perflib_sgemm_c(int M, int N, int K,
                     const float *A, int lda,
                     const float *B, int ldb,
                     float *C, int ldc)
{
    for (int i = 0; i < M; ++i) {
        float *ci = C + (size_t)i * ldc;
        const float *ai = A + (size_t)i * lda;
        for (int k = 0; k < K; ++k) {
            const float aik = ai[k];
            const float *bk = B + (size_t)k * ldb;
            for (int j = 0; j < N; ++j)
                ci[j] += aik * bk[j];
        }
    }
}
