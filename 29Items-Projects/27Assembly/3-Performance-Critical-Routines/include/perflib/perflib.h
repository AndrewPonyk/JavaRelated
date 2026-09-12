/*
 * perflib.h — umbrella header for the Performance-Critical Routines library.
 *
 *   #include <perflib/perflib.h>
 *   perflib_init();                 // once, at startup: detect CPU + bind impls
 *   perflib_sgemm(...);             // calls the fastest legal implementation
 *
 * Stable C ABI. SemVer: ABI breaks bump PERFLIB_VERSION_MAJOR (and SONAME).
 */
#ifndef PERFLIB_H
#define PERFLIB_H

#include "cpu_features.h"
#include "matrix.h"
#include "string_ops.h"

#ifdef __cplusplus
extern "C" {
#endif

#define PERFLIB_VERSION_MAJOR 0
#define PERFLIB_VERSION_MINOR 1
#define PERFLIB_VERSION_PATCH 0
#define PERFLIB_VERSION_STRING "0.1.0"

/* Implementation tier selected by the dispatcher. */
typedef enum perflib_isa {
    PERFLIB_ISA_SCALAR = 0, /* portable C reference */
    PERFLIB_ISA_AVX2   = 1, /* AVX2 + FMA kernels   */
    PERFLIB_ISA_AVX512 = 2  /* reserved (future)    */
} perflib_isa_t;

/*
 * Detect CPU features and bind the dispatched public symbols to the fastest
 * legal implementation. Safe to call multiple times. Returns the selected tier.
 * Thread-safety: call once before first use of any dispatched routine (e.g.
 * from main() or a constructor); after that the dispatch table is read-only.
 */
perflib_isa_t perflib_init(void);

/* Returns the tier chosen by the most recent perflib_init() (SCALAR if never). */
perflib_isa_t perflib_active_isa(void);

/* Human-readable version + active tier, e.g. "perflib 0.1.0 [AVX2]". */
const char *perflib_version_string(void);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* PERFLIB_H */
