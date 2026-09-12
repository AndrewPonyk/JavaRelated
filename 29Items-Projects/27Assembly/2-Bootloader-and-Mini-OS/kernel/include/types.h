/* =============================================================================
 *  types.h  --  Fixed-width integer types for a freestanding kernel
 *
 *  We cannot #include <stdint.h> in all toolchains when building -ffreestanding
 *  without the right headers, so we define the minimal set ourselves. These are
 *  correct for the i386 / ILP32 target this kernel runs on.
 * ===========================================================================*/
#ifndef MINIOS_TYPES_H
#define MINIOS_TYPES_H

/* Fixed-width aliases shared by the kernel and the host unit tests.
 *
 * The kernel is compiled with -ffreestanding (so __STDC_HOSTED__ == 0) and gets
 * our own definitions. Host test builds (__STDC_HOSTED__ == 1) defer to the
 * standard headers instead, so `size_t`/`bool` don't clash with <stdio.h> etc.
 * This dual mode is what makes the pure logic in this kernel host-testable. */

#if defined(__STDC_HOSTED__) && (__STDC_HOSTED__ == 1)

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

typedef uint8_t  u8;   typedef int8_t   i8;
typedef uint16_t u16;  typedef int16_t  i16;
typedef uint32_t u32;  typedef int32_t  i32;
typedef uint64_t u64;  typedef int64_t  i64;
/* size_t, uintptr_t, bool/true/false, NULL come from the standard headers. */

#else  /* freestanding kernel build */

typedef unsigned char      u8;
typedef signed char        i8;
typedef unsigned short     u16;
typedef signed short       i16;
typedef unsigned int       u32;
typedef signed int         i32;
typedef unsigned long long u64;
typedef signed long long   i64;

typedef u32 size_t;         /* 32-bit target */
typedef u32 uintptr_t;

#ifndef NULL
#define NULL ((void *)0)
#endif

/* C11 has _Bool; provide a friendly alias without pulling in <stdbool.h>. */
typedef u8 bool;
#define true  1
#define false 0

#endif /* __STDC_HOSTED__ */

#endif /* MINIOS_TYPES_H */
