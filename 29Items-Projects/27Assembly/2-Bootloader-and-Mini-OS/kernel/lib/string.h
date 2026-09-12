/* =============================================================================
 *  string.h  --  Freestanding string utilities (no libc available)
 *
 *  memcpy/memset/memmove live in mm/memory.c; this is the string-oriented
 *  subset. All are simple, dependency-free, and unit-tested on the host.
 * ===========================================================================*/
#ifndef MINIOS_STRING_H
#define MINIOS_STRING_H

#include "../include/types.h"

size_t strlen(const char *s);
int    strcmp(const char *a, const char *b);
int    strncmp(const char *a, const char *b, size_t n);
char  *strcpy(char *dst, const char *src);
char  *strncpy(char *dst, const char *src, size_t n);

/* Integer -> string. `base` is 10 or 16; writes into caller-provided `buf`
 * (must be >= 12 bytes for 32-bit values) and returns it. */
char  *itoa(u32 value, char *buf, int base);

#endif /* MINIOS_STRING_H */
