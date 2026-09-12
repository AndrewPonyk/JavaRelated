/*
 * perflib/cpu_features.h — runtime CPU capability detection.
 *
 * The library never executes an instruction the running CPU does not support;
 * detection results drive the dispatch layer (see dispatch.c). Detection is a
 * one-time CPUID query whose result is cached.
 */
#ifndef PERFLIB_CPU_FEATURES_H
#define PERFLIB_CPU_FEATURES_H

#ifdef __cplusplus
extern "C" {
#endif

/* Detected instruction-set features relevant to this library. 1 = present. */
typedef struct perflib_cpu_features {
    int sse2;     /* baseline on all x86-64 */
    int avx;      /* 256-bit float                       */
    int avx2;     /* 256-bit integer + gather            */
    int fma;      /* FMA3 (fused multiply-add)           */
    int bmi1;     /* tzcnt/andn/blsr...                  */
    int bmi2;     /* bzhi/pdep/pext...                   */
    int avx512f;  /* AVX-512 foundation (future path)    */
    char brand[64]; /* CPU brand string (best-effort)    */
} perflib_cpu_features_t;

/*
 * Fill *out with detected features. Idempotent and cheap after the first call
 * (result is cached internally). `out` must be non-NULL.
 */
void perflib_detect_cpu(perflib_cpu_features_t *out);

/* Convenience predicates (each triggers/uses the cached detection). */
int perflib_has_avx2(void);
int perflib_has_fma(void);
int perflib_has_avx512f(void);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* PERFLIB_CPU_FEATURES_H */
