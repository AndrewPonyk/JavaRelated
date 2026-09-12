/*
 * ppmon.h — Umbrella header, version macros, and shared status type.
 *
 * Portable Process Monitor (ppmon)
 * Public API surface. Include this from application code; individual modules
 * may be included directly for finer-grained dependencies.
 */
#ifndef PPMON_H
#define PPMON_H

#define PPMON_VERSION_MAJOR 0
#define PPMON_VERSION_MINOR 1
#define PPMON_VERSION_PATCH 0
#define PPMON_VERSION_STRING "0.1.0"

#include <stddef.h>
#include <stdint.h>

/*
 * Unified result type. Every fallible ppmon function returns this; output
 * parameters are only valid when the function returns PPMON_OK. Collection
 * modules translate NTSTATUS / GetLastError() / PDH_STATUS into these values at
 * the boundary so the domain layer never sees raw OS codes.
 */
typedef enum ppmon_status {
    PPMON_OK = 0,
    PPMON_ERR_INVALID_ARG,   /* caller passed a NULL/out-of-range argument      */
    PPMON_ERR_NO_MEMORY,     /* allocation failed                               */
    PPMON_ERR_ACCESS_DENIED, /* insufficient privilege for this object          */
    PPMON_ERR_NOT_FOUND,     /* process/counter/path does not exist             */
    PPMON_ERR_OS,            /* generic OS-level failure (see logs)             */
    PPMON_ERR_IO,            /* file/socket I/O failure                         */
    PPMON_ERR_AGAIN,         /* transient; retry is appropriate                 */
    PPMON_ERR_UNSUPPORTED    /* feature not available on this platform/build    */
} ppmon_status_t;

/* Human-readable name for a status code (for logging). Never returns NULL. */
const char *ppmon_status_str(ppmon_status_t status);

#endif /* PPMON_H */
