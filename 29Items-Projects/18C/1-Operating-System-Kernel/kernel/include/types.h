/*
 * types.h — Freestanding fixed-width types for the kernel.
 *
 * We are -ffreestanding with no libc, so we define the small set of types the
 * kernel relies on instead of pulling <stdint.h>/<stddef.h> from a host.
 */
#ifndef KERNEL_TYPES_H
#define KERNEL_TYPES_H

typedef unsigned char      uint8_t;
typedef signed char        int8_t;
typedef unsigned short     uint16_t;
typedef signed short       int16_t;
typedef unsigned int       uint32_t;
typedef signed int         int32_t;
typedef unsigned long long uint64_t;
typedef signed long long   int64_t;

/* x86-64: pointers and word size are 64-bit. Use the compiler's own builtin
 * types so these are byte-for-byte identical to the host's <stddef.h> when the
 * unit tests pull both in — avoids "conflicting types for size_t". They are
 * always available, even under -ffreestanding (no header needed). */
typedef __SIZE_TYPE__      size_t;
typedef __PTRDIFF_TYPE__   ssize_t;
typedef __UINTPTR_TYPE__   uintptr_t;
typedef __INTPTR_TYPE__    intptr_t;

typedef int                pid_t;

#ifndef __cplusplus
typedef _Bool bool;
#define true  1
#define false 0
#endif

#ifndef NULL
#define NULL ((void *)0)
#endif

/* errno-style negative return codes used throughout the kernel. */
#define EOK     0
#define EPERM   1
#define ENOENT  2
#define EIO     5
#define ENOMEM  12
#define EFAULT  14
#define EBUSY   16
#define EEXIST  17
#define EINVAL  22
#define ENOSPC  28

#endif /* KERNEL_TYPES_H */
