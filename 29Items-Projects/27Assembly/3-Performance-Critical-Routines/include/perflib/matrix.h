/*
 * perflib/matrix.h — single-precision linear-algebra routines.
 *
 * Three symbols per routine:
 *   perflib_xxx       dispatched (best available impl; bind via perflib_init)
 *   perflib_xxx_c     portable C reference (correctness ground truth)
 *   perflib_xxx_avx2  hand-written AVX2/FMA kernel (NASM)
 *
 * Row-major storage. Leading dimension (ld*) is the row stride in ELEMENTS and
 * must be >= the number of columns. Pointers SHOULD be 32-byte aligned for the
 * AVX2 path; correctness does not require it (kernels use unaligned moves), but
 * alignment improves throughput.
 */
#ifndef PERFLIB_MATRIX_H
#define PERFLIB_MATRIX_H

#ifdef __cplusplus
extern "C" {
#endif

/* y[i] += a * x[i]   (SAXPY, BLAS level 1), n elements. */
void  perflib_saxpy     (int n, float a, const float *x, float *y);
void  perflib_saxpy_c   (int n, float a, const float *x, float *y);
void  perflib_saxpy_avx2(int n, float a, const float *x, float *y);

/* return sum_i x[i]*y[i]   (SDOT, BLAS level 1), n elements. */
float perflib_sdot      (int n, const float *x, const float *y);
float perflib_sdot_c    (int n, const float *x, const float *y);
float perflib_sdot_avx2 (int n, const float *x, const float *y);

/*
 * C := A * B + C   (SGEMM, BLAS level 3, no alpha/beta/transpose for brevity).
 *   A is M x K (lda), B is K x N (ldb), C is M x N (ldc), all row-major.
 * Caller must zero C first if a pure product is desired.
 */
void perflib_sgemm     (int M, int N, int K,
                        const float *A, int lda,
                        const float *B, int ldb,
                        float *C, int ldc);
void perflib_sgemm_c   (int M, int N, int K,
                        const float *A, int lda,
                        const float *B, int ldb,
                        float *C, int ldc);
void perflib_sgemm_avx2(int M, int N, int K,
                        const float *A, int lda,
                        const float *B, int ldb,
                        float *C, int ldc);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* PERFLIB_MATRIX_H */
