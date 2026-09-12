/* test_matrix.c — AVX2 kernels vs C reference, ULP/relative bounded. */
#include "perflib/perflib.h"
#include "test_framework.h"
#include <stdlib.h>

/* xorshift32 — deterministic pseudo-random floats in [-0.5, 0.5). */
static unsigned s_rng = 2463534242u;
static float frand(void)
{
    s_rng ^= s_rng << 13; s_rng ^= s_rng >> 17; s_rng ^= s_rng << 5;
    return (float)(s_rng >> 8) / (float)(1u << 24) - 0.5f;
}

static void test_saxpy(void)
{
    const int sizes[] = {0, 1, 7, 8, 9, 15, 16, 17, 64, 1000};
    for (size_t i = 0; i < sizeof sizes / sizeof *sizes; ++i) {
        int n = sizes[i], m = n ? n : 1;
        float *x  = malloc((size_t)m * sizeof(float));
        float *yc = malloc((size_t)m * sizeof(float));
        float *ya = malloc((size_t)m * sizeof(float));
        for (int j = 0; j < n; ++j) { x[j] = frand(); float v = frand(); yc[j] = v; ya[j] = v; }
        const float a = 1.7f;
        perflib_saxpy_c(n, a, x, yc);
        perflib_saxpy_avx2(n, a, x, ya);
        for (int j = 0; j < n; ++j) CHECK_CLOSE(ya[j], yc[j], 1e-5f, 1e-6f);
        free(x); free(yc); free(ya);
    }
}

static void test_sdot(void)
{
    const int sizes[] = {0, 1, 7, 8, 9, 15, 16, 17, 64, 1000};
    for (size_t i = 0; i < sizeof sizes / sizeof *sizes; ++i) {
        int n = sizes[i], m = n ? n : 1;
        float *x = malloc((size_t)m * sizeof(float));
        float *y = malloc((size_t)m * sizeof(float));
        for (int j = 0; j < n; ++j) { x[j] = frand(); y[j] = frand(); }
        float rc = perflib_sdot_c(n, x, y);
        float ra = perflib_sdot_avx2(n, x, y);
        CHECK_CLOSE(ra, rc, 1e-4f, 1e-5f);
        free(x); free(y);
    }
}

static void test_sgemm(void)
{
    /* Mix of N multiple-of-8 (AVX2 path) and not (dispatched C fallback). */
    const int dims[][3] = {
        {1, 8, 1}, {3, 8, 5}, {8, 8, 8}, {16, 16, 16},
        {5, 24, 7}, {4, 5, 6}, {10, 3, 10}
    };
    for (size_t t = 0; t < sizeof dims / sizeof *dims; ++t) {
        int M = dims[t][0], N = dims[t][1], K = dims[t][2];
        float *A  = malloc((size_t)M * K * sizeof(float));
        float *B  = malloc((size_t)K * N * sizeof(float));
        float *Cc = calloc((size_t)M * N, sizeof(float));
        float *Ca = calloc((size_t)M * N, sizeof(float));
        for (int i = 0; i < M * K; ++i) A[i] = frand();
        for (int i = 0; i < K * N; ++i) B[i] = frand();
        perflib_sgemm_c(M, N, K, A, K, B, N, Cc, N);
        perflib_sgemm  (M, N, K, A, K, B, N, Ca, N); /* dispatched wrapper */
        for (int i = 0; i < M * N; ++i) CHECK_CLOSE(Ca[i], Cc[i], 1e-3f, 1e-4f);
        free(A); free(B); free(Cc); free(Ca);
    }
}

void test_matrix(void)
{
    if (!perflib_has_avx2()) {
        fprintf(stderr, "  [skip] AVX2 not present at runtime\n");
        return;
    }
    test_saxpy();
    test_sdot();
    test_sgemm();
}
