/*
 * string_ops_c.c — portable C reference implementations for string/memory scans.
 * Correctness ground truth + auto-vectorized baseline.
 */
#include "perflib/string_ops.h"

size_t perflib_strlen_c(const char *s)
{
    const char *p = s;
    while (*p)
        ++p;
    return (size_t)(p - s);
}

void *perflib_memchr_c(const void *s, int c, size_t n)
{
    const unsigned char *p = (const unsigned char *)s;
    const unsigned char target = (unsigned char)c;
    for (size_t i = 0; i < n; ++i) {
        if (p[i] == target)
            return (void *)(p + i);
    }
    return NULL;
}
