/*
 * perflib/string_ops.h — SIMD string / memory scan routines.
 *
 * Same three-symbol convention as matrix.h (dispatched / _c / _avx2).
 *
 * Page-safety invariant: the AVX2 scanners align reads down to a 32-byte
 * boundary and mask the head, so they never read past the byte that terminates
 * the scan into an unmapped page. This is verified by a guard-page test.
 */
#ifndef PERFLIB_STRING_OPS_H
#define PERFLIB_STRING_OPS_H

#include <stddef.h> /* size_t */

#ifdef __cplusplus
extern "C" {
#endif

/* Length of NUL-terminated string s (like strlen). */
size_t perflib_strlen     (const char *s);
size_t perflib_strlen_c   (const char *s);
size_t perflib_strlen_avx2(const char *s);

/*
 * Find first byte equal to (unsigned char)c within the first n bytes of s.
 * Returns pointer to the byte, or NULL if not found (like memchr).
 */
void *perflib_memchr     (const void *s, int c, size_t n);
void *perflib_memchr_c   (const void *s, int c, size_t n);
void *perflib_memchr_avx2(const void *s, int c, size_t n);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* PERFLIB_STRING_OPS_H */
