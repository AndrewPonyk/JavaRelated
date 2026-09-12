/* bench_matrix.c — saxpy / sdot / sgemm: AVX2 vs auto-vectorized C. */
#include "perflib/perflib.h"
#include "bench_util.h"
#include <stdlib.h>

static unsigned s_rng = 88172645u;
static float frand(void)
{
    s_rng ^= s_rng << 13; s_rng ^= s_rng >> 17; s_rng ^= s_rng << 5;
    return (float)(s_rng >> 8) / (float)(1u << 24) - 0.5f;
}

/* ----------------------------- saxpy (GB/s) ----------------------------- */
typedef struct { int n; const float *x; float *y; float a; int inner; } saxpy_ctx;
static void t_saxpy_c   (void *p){ saxpy_ctx *c=p; for(int i=0;i<c->inner;i++) perflib_saxpy_c   (c->n,c->a,c->x,c->y); perf_sink(c->y); }
static void t_saxpy_avx2(void *p){ saxpy_ctx *c=p; for(int i=0;i<c->inner;i++) perflib_saxpy_avx2(c->n,c->a,c->x,c->y); perf_sink(c->y); }

static void bench_saxpy(FILE *f, int repeats, int warmup)
{
    const int sizes[] = {1 << 10, 1 << 14, 1 << 20};
    for (size_t s = 0; s < sizeof sizes / sizeof *sizes; ++s) {
        int n = sizes[s], inner = n < 100000 ? 200 : 8;
        float *x = malloc((size_t)n * sizeof(float));
        float *y = malloc((size_t)n * sizeof(float));
        uint64_t *samp = malloc((size_t)repeats * sizeof(uint64_t));
        for (int i = 0; i < n; ++i) { x[i] = frand(); y[i] = frand(); }
        saxpy_ctx ctx = { n, x, y, 1.5f, inner };
        uint64_t mc = bench_measure(t_saxpy_c,    &ctx, warmup, repeats, samp);
        uint64_t ma = bench_measure(t_saxpy_avx2, &ctx, warmup, repeats, samp);
        double bytes = 3.0 * (double)n * sizeof(float); /* read x,y + write y */
        double nc = (double)mc / inner, na = (double)ma / inner;
        bench_row_t rc = {"saxpy","c",   n,(uint64_t)nc,bytes/nc,"GB/s",1.0};
        bench_row_t ra = {"saxpy","avx2",n,(uint64_t)na,bytes/na,"GB/s",nc/na};
        bench_emit_csv_row(f,&rc); bench_emit_csv_row(f,&ra);
        free(x); free(y); free(samp);
    }
}

/* ----------------------------- sdot (GFLOP/s) --------------------------- */
typedef struct { int n; const float *x; const float *y; volatile float sink; int inner; } sdot_ctx;
static void t_sdot_c   (void *p){ sdot_ctx *c=p; float s=0; for(int i=0;i<c->inner;i++) s+=perflib_sdot_c   (c->n,c->x,c->y); c->sink=s; }
static void t_sdot_avx2(void *p){ sdot_ctx *c=p; float s=0; for(int i=0;i<c->inner;i++) s+=perflib_sdot_avx2(c->n,c->x,c->y); c->sink=s; }

static void bench_sdot(FILE *f, int repeats, int warmup)
{
    const int sizes[] = {1 << 10, 1 << 14, 1 << 20};
    for (size_t s = 0; s < sizeof sizes / sizeof *sizes; ++s) {
        int n = sizes[s], inner = n < 100000 ? 200 : 8;
        float *x = malloc((size_t)n * sizeof(float));
        float *y = malloc((size_t)n * sizeof(float));
        uint64_t *samp = malloc((size_t)repeats * sizeof(uint64_t));
        for (int i = 0; i < n; ++i) { x[i] = frand(); y[i] = frand(); }
        sdot_ctx ctx = { n, x, y, 0.0f, inner };
        uint64_t mc = bench_measure(t_sdot_c,    &ctx, warmup, repeats, samp);
        uint64_t ma = bench_measure(t_sdot_avx2, &ctx, warmup, repeats, samp);
        double flops = 2.0 * (double)n; /* mul + add */
        double nc = (double)mc / inner, na = (double)ma / inner;
        bench_row_t rc = {"sdot","c",   n,(uint64_t)nc,flops/nc,"GFLOP/s",1.0};
        bench_row_t ra = {"sdot","avx2",n,(uint64_t)na,flops/na,"GFLOP/s",nc/na};
        bench_emit_csv_row(f,&rc); bench_emit_csv_row(f,&ra);
        free(x); free(y); free(samp);
    }
}

/* ----------------------------- sgemm (GFLOP/s) -------------------------- */
typedef struct { int M,N,K; const float *A; const float *B; float *C; int inner; } sgemm_ctx;
static void t_sgemm_c   (void *p){ sgemm_ctx *c=p; for(int i=0;i<c->inner;i++) perflib_sgemm_c   (c->M,c->N,c->K,c->A,c->K,c->B,c->N,c->C,c->N); perf_sink(c->C); }
static void t_sgemm_avx2(void *p){ sgemm_ctx *c=p; for(int i=0;i<c->inner;i++) perflib_sgemm_avx2(c->M,c->N,c->K,c->A,c->K,c->B,c->N,c->C,c->N); perf_sink(c->C); }

static void bench_sgemm(FILE *f, int repeats, int warmup)
{
    const int dims[] = {64, 128, 256}; /* square, multiple of 8 -> AVX2 path */
    for (size_t s = 0; s < sizeof dims / sizeof *dims; ++s) {
        int D = dims[s], inner = D <= 128 ? 4 : 1;
        float *A = malloc((size_t)D * D * sizeof(float));
        float *B = malloc((size_t)D * D * sizeof(float));
        float *C = calloc((size_t)D * D, sizeof(float));
        uint64_t *samp = malloc((size_t)repeats * sizeof(uint64_t));
        for (int i = 0; i < D * D; ++i) { A[i] = frand(); B[i] = frand(); }
        sgemm_ctx ctx = { D, D, D, A, B, C, inner };
        uint64_t mc = bench_measure(t_sgemm_c,    &ctx, warmup, repeats, samp);
        uint64_t ma = bench_measure(t_sgemm_avx2, &ctx, warmup, repeats, samp);
        double flops = 2.0 * (double)D * D * D; /* MNK fmas */
        double nc = (double)mc / inner, na = (double)ma / inner;
        bench_row_t rc = {"sgemm","c",   D,(uint64_t)nc,flops/nc,"GFLOP/s",1.0};
        bench_row_t ra = {"sgemm","avx2",D,(uint64_t)na,flops/na,"GFLOP/s",nc/na};
        bench_emit_csv_row(f,&rc); bench_emit_csv_row(f,&ra);
        free(A); free(B); free(C); free(samp);
    }
}

void bench_matrix(FILE *csv, int repeats, int warmup)
{
    bench_saxpy(csv, repeats, warmup);
    bench_sdot (csv, repeats, warmup);
    bench_sgemm(csv, repeats, warmup);
}
