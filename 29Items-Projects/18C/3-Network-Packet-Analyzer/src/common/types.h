/* SPDX-License-Identifier: MIT
 *
 * common/types.h — project-wide primitive aliases and the result/error contract.
 *
 * This header is a LEAF: it must not include any other npa header. Everything
 * else may include it. Keep it tiny and dependency-free.
 */
#ifndef NPA_COMMON_TYPES_H
#define NPA_COMMON_TYPES_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/* Fixed-width convenience aliases (on-wire decoding deals in exact widths). */
typedef uint8_t  u8;
typedef uint16_t u16;
typedef uint32_t u32;
typedef uint64_t u64;
typedef int8_t   i8;
typedef int16_t  i16;
typedef int32_t  i32;
typedef int64_t  i64;

/*
 * npa_result_t — the single error vocabulary used across the codebase.
 *
 * Convention: functions return NPA_OK (0) on success and a negative-meaning
 * enum otherwise. Any out-parameters are only valid when NPA_OK is returned.
 * Never return bare -1 / errno; map into this enum so callers can switch().
 */
typedef enum {
    NPA_OK = 0,            /* success                                          */
    NPA_ERR_INVAL,         /* invalid argument / precondition violated         */
    NPA_ERR_NOMEM,         /* allocation failed                                */
    NPA_ERR_IO,            /* file / device / syscall I/O error                */
    NPA_ERR_TRUNCATED,     /* decode: buffer shorter than the field needs      */
    NPA_ERR_UNSUPPORTED,   /* decode: known-but-unhandled protocol/feature     */
    NPA_ERR_MALFORMED,     /* decode: field values violate the spec            */
    NPA_ERR_AGAIN,         /* would block / try again (e.g. ring empty)        */
    NPA_ERR_FULL,          /* bounded container at capacity (e.g. ring full)   */
    NPA_ERR_NOTFOUND,      /* lookup miss                                      */
    NPA_ERR_INTERNAL,      /* invariant broken — a bug, not bad input          */
} npa_result_t;

/* Human-readable name for a result code (defined in util/log.c). */
const char *npa_result_str(npa_result_t r);

/* Branch-prediction + unused hints kept here so every TU can use them. */
#if defined(__GNUC__) || defined(__clang__)
#  define NPA_LIKELY(x)   __builtin_expect(!!(x), 1)
#  define NPA_UNLIKELY(x) __builtin_expect(!!(x), 0)
#  define NPA_UNUSED      __attribute__((unused))
#  define NPA_PACKED      __attribute__((packed))
#else
#  define NPA_LIKELY(x)   (x)
#  define NPA_UNLIKELY(x) (x)
#  define NPA_UNUSED
#  define NPA_PACKED
#endif

#define NPA_ARRAY_LEN(a) (sizeof(a) / sizeof((a)[0]))

#endif /* NPA_COMMON_TYPES_H */
