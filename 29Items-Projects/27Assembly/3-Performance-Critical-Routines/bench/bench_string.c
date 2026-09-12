/* bench_string.c — strlen / memchr: AVX2 vs C (and implicitly libc). */
#include "perflib/perflib.h"
#include "bench_util.h"
#include <stdlib.h>
#include <string.h>

/* ----------------------------- strlen (GB/s) --------------------------- */
typedef struct { const char *s; volatile size_t sink; int inner; } strlen_ctx;
static void t_strlen_c   (void *p){ strlen_ctx *c=p; size_t r=0; for(int i=0;i<c->inner;i++) r+=perflib_strlen_c   (c->s); c->sink=r; }
static void t_strlen_avx2(void *p){ strlen_ctx *c=p; size_t r=0; for(int i=0;i<c->inner;i++) r+=perflib_strlen_avx2(c->s); c->sink=r; }

static void bench_strlen(FILE *f, int repeats, int warmup)
{
    const int sizes[] = {64, 1024, 1 << 16, 1 << 20};
    for (size_t s = 0; s < sizeof sizes / sizeof *sizes; ++s) {
        int n = sizes[s], inner = n < 100000 ? 1000 : 20;
        char *buf = malloc((size_t)n + 1);
        memset(buf, 'a', (size_t)n);
        buf[n] = '\0';
        uint64_t *samp = malloc((size_t)repeats * sizeof(uint64_t));
        strlen_ctx ctx = { buf, 0, inner };
        uint64_t mc = bench_measure(t_strlen_c,    &ctx, warmup, repeats, samp);
        uint64_t ma = bench_measure(t_strlen_avx2, &ctx, warmup, repeats, samp);
        double bytes = (double)n;
        double nc = (double)mc / inner, na = (double)ma / inner;
        bench_row_t rc = {"strlen","c",   n,(uint64_t)nc,bytes/nc,"GB/s",1.0};
        bench_row_t ra = {"strlen","avx2",n,(uint64_t)na,bytes/na,"GB/s",nc/na};
        bench_emit_csv_row(f,&rc); bench_emit_csv_row(f,&ra);
        free(buf); free(samp);
    }
}

/* ----------------------------- memchr (GB/s) --------------------------- */
/* Worst case: byte not present, so the whole buffer is scanned. */
typedef struct { const char *s; size_t n; volatile void *sink; int inner; } memchr_ctx;
static void t_memchr_c   (void *p){ memchr_ctx *c=p; void *r=NULL; for(int i=0;i<c->inner;i++) r=perflib_memchr_c   (c->s,'Z',c->n); c->sink=r; }
static void t_memchr_avx2(void *p){ memchr_ctx *c=p; void *r=NULL; for(int i=0;i<c->inner;i++) r=perflib_memchr_avx2(c->s,'Z',c->n); c->sink=r; }

static void bench_memchr(FILE *f, int repeats, int warmup)
{
    const int sizes[] = {1024, 1 << 16, 1 << 20};
    for (size_t s = 0; s < sizeof sizes / sizeof *sizes; ++s) {
        int n = sizes[s], inner = n < 100000 ? 1000 : 20;
        char *buf = malloc((size_t)n);
        memset(buf, 'a', (size_t)n);              /* 'Z' never present */
        uint64_t *samp = malloc((size_t)repeats * sizeof(uint64_t));
        memchr_ctx ctx = { buf, (size_t)n, NULL, inner };
        uint64_t mc = bench_measure(t_memchr_c,    &ctx, warmup, repeats, samp);
        uint64_t ma = bench_measure(t_memchr_avx2, &ctx, warmup, repeats, samp);
        double bytes = (double)n;
        double nc = (double)mc / inner, na = (double)ma / inner;
        bench_row_t rc = {"memchr","c",   n,(uint64_t)nc,bytes/nc,"GB/s",1.0};
        bench_row_t ra = {"memchr","avx2",n,(uint64_t)na,bytes/na,"GB/s",nc/na};
        bench_emit_csv_row(f,&rc); bench_emit_csv_row(f,&ra);
        free(buf); free(samp);
    }
}

void bench_string(FILE *csv, int repeats, int warmup)
{
    bench_strlen(csv, repeats, warmup);
    bench_memchr(csv, repeats, warmup);
}
