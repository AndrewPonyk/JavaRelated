/*
 * bench_util.h — timing, statistics, optimizer barriers, CSV emit.
 *
 * Methodology notes (see docs/TECH-NOTES.md §3.6): always warm up, sample the
 * steady state, take the median (robust to OS jitter), and *consume* outputs so
 * the compiler cannot delete the work being measured.
 */
#ifndef PERFLIB_BENCH_UTIL_H
#define PERFLIB_BENCH_UTIL_H

#include <stdint.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>

/* ---- monotonic nanosecond clock ---- */
#if defined(_WIN32)
#  include <windows.h>
static inline uint64_t perf_now_ns(void)
{
    static double scale = 0.0;
    LARGE_INTEGER c;
    if (scale == 0.0) {
        LARGE_INTEGER f; QueryPerformanceFrequency(&f);
        scale = 1e9 / (double)f.QuadPart;
    }
    QueryPerformanceCounter(&c);
    return (uint64_t)((double)c.QuadPart * scale);
}
#else
#  include <time.h>
static inline uint64_t perf_now_ns(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ull + (uint64_t)ts.tv_nsec;
}
#endif

/* ---- optimizer barrier: keep the benchmarked result "live" ---- */
#if defined(__GNUC__) || defined(__clang__)
static inline void perf_sink(const void *p)
{
    __asm__ volatile("" : : "g"(p) : "memory");
}
#else
static const volatile void *perf_sink_v;
static inline void perf_sink(const void *p) { perf_sink_v = p; }
#endif

/* ---- statistics ---- */
static inline int perf_cmp_u64(const void *a, const void *b)
{
    uint64_t x = *(const uint64_t *)a, y = *(const uint64_t *)b;
    return (x > y) - (x < y);
}
static inline uint64_t perf_median(uint64_t *v, int n)
{
    qsort(v, (size_t)n, sizeof *v, perf_cmp_u64);
    return v[n / 2];
}

/* ---- generic measurement: warmup then `repeats` timed samples ---- */
typedef void (*bench_thunk)(void *ctx);

static inline uint64_t bench_measure(bench_thunk fn, void *ctx,
                                     int warmup, int repeats, uint64_t *samples)
{
    for (int i = 0; i < warmup; ++i) fn(ctx);
    for (int i = 0; i < repeats; ++i) {
        uint64_t t0 = perf_now_ns();
        fn(ctx);
        uint64_t t1 = perf_now_ns();
        samples[i] = t1 - t0;
    }
    return perf_median(samples, repeats);
}

/* ---- result row (matches schema/benchmark_results.schema.json) ---- */
typedef struct {
    const char *routine;
    const char *impl;      /* "c" | "avx2" */
    long        size;
    uint64_t    ns_median; /* per single call */
    double      throughput;
    const char *unit;      /* "GB/s" | "GFLOP/s" */
    double      speedup;   /* vs C reference (1.0 for the C row) */
} bench_row_t;

static inline void bench_emit_csv_header(FILE *f)
{
    fprintf(f, "routine,impl,size,ns_per_call,throughput,unit,speedup\n");
}
static inline void bench_emit_csv_row(FILE *f, const bench_row_t *r)
{
    fprintf(f, "%s,%s,%ld,%llu,%.4f,%s,%.4f\n",
            r->routine, r->impl, r->size,
            (unsigned long long)r->ns_median, r->throughput, r->unit, r->speedup);
    /* mirror a compact line to stderr for live feedback */
    fprintf(stderr, "  %-7s %-5s size=%-9ld %8.2f %-8s  x%.2f\n",
            r->routine, r->impl, r->size, r->throughput, r->unit, r->speedup);
}

/* suite entry points (defined in bench_matrix.c / bench_string.c) */
void bench_matrix(FILE *csv, int repeats, int warmup);
void bench_string(FILE *csv, int repeats, int warmup);

#endif /* PERFLIB_BENCH_UTIL_H */
