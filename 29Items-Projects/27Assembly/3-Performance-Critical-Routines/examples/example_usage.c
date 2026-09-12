/*
 * example_usage.c — minimal consumer of libperflib.
 *
 *   cc example_usage.c -lperflib -o example      (or: pkg-config --libs perflib)
 *
 * Shows the canonical lifecycle: init once, then call the dispatched routines.
 */
#include <perflib/perflib.h>
#include <stdio.h>

int main(void)
{
    /* 1) Detect CPU and bind the fastest legal implementations. Call once. */
    perflib_isa_t isa = perflib_init();
    printf("%s (ISA tier %d)\n", perflib_version_string(), (int)isa);

    /* 2) BLAS-1: y = a*x + y */
    float x[8] = {1, 2, 3, 4, 5, 6, 7, 8};
    float y[8] = {0, 0, 0, 0, 0, 0, 0, 0};
    perflib_saxpy(8, 2.0f, x, y);          /* y = 2*x */
    printf("saxpy y[0..7] =");
    for (int i = 0; i < 8; ++i) printf(" %.1f", y[i]);
    printf("\n");

    /* 3) BLAS-1: dot product */
    printf("sdot(x,x) = %.1f\n", perflib_sdot(8, x, x)); /* 1+4+...+64 = 204 */

    /* 4) BLAS-3: C = A*B for two 2x2 matrices (zero C first) */
    float A[4] = {1, 2, 3, 4};
    float B[4] = {5, 6, 7, 8};
    float C[4] = {0, 0, 0, 0};
    perflib_sgemm(2, 2, 2, A, 2, B, 2, C, 2);
    printf("sgemm C = [%.0f %.0f; %.0f %.0f]\n", C[0], C[1], C[2], C[3]);

    /* 5) String op */
    const char *msg = "hello, performance-critical world";
    printf("strlen = %zu\n", perflib_strlen(msg));

    return 0;
}
