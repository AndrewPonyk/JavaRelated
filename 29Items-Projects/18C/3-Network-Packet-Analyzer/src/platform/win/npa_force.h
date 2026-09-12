/* SPDX-License-Identifier: MIT
 *
 * platform/win/npa_force.h — force-included (/FI) on Windows builds to fill the
 * small POSIX/libc gaps the sources assume. Implementations of clock_gettime /
 * nanosleep live in platform/win/win_compat.c (kept out of pure TUs so
 * <windows.h> isn't dragged everywhere).
 */
#ifndef NPA_WIN_FORCE_H
#define NPA_WIN_FORCE_H
#if defined(_WIN32)

#include <time.h>      /* struct timespec, timespec_get (C11) */
#include <stdlib.h>    /* _putenv_s                            */

#pragma warning(disable : 4505)  /* unreferenced static (these shims) */

/* MSVC's 3-arg strtok_s matches POSIX strtok_r. */
#define strtok_r strtok_s

/* POSIX env helpers (MSVC provides _putenv_s). */
static __inline int npa_setenv(const char *n, const char *v, int overwrite) {
    (void)overwrite; return _putenv_s(n, v);
}
static __inline int npa_unsetenv(const char *n) { return _putenv_s(n, ""); }
#define setenv   npa_setenv
#define unsetenv npa_unsetenv

#ifndef CLOCK_REALTIME
#  define CLOCK_REALTIME 0
#endif
#ifndef CLOCK_MONOTONIC
#  define CLOCK_MONOTONIC 1
#endif

/* Provided by platform/win/win_compat.c. */
int npa_clock_gettime(int clk, struct timespec *ts);
int npa_nanosleep(const struct timespec *req, struct timespec *rem);

#define clock_gettime npa_clock_gettime
#define nanosleep     npa_nanosleep

#endif /* _WIN32 */
#endif /* NPA_WIN_FORCE_H */
