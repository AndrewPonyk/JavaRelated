/* SPDX-License-Identifier: MIT
 *
 * platform/win/win_compat.c — Windows implementations of clock_gettime and
 * nanosleep (declared in npa_force.h). Isolated here so <windows.h> stays out
 * of the rest of the build. Compiles to nothing off-Win32.
 */
#if defined(_WIN32)

#ifndef WIN32_LEAN_AND_MEAN
#  define WIN32_LEAN_AND_MEAN
#endif
#ifndef NOMINMAX
#  define NOMINMAX
#endif
#include <windows.h>
#include <time.h>

/* npa_force.h (#define clock_gettime npa_clock_gettime) is force-included, so
 * spell the definitions with their real names explicitly. */
#undef clock_gettime
#undef nanosleep

int npa_clock_gettime(int clk, struct timespec *ts) {
    if (clk == 1 /* CLOCK_MONOTONIC */) {
        LARGE_INTEGER freq, cnt;
        QueryPerformanceFrequency(&freq);
        QueryPerformanceCounter(&cnt);
        ts->tv_sec  = (time_t)(cnt.QuadPart / freq.QuadPart);
        ts->tv_nsec = (long)(((cnt.QuadPart % freq.QuadPart) * 1000000000LL) / freq.QuadPart);
        return 0;
    }
    return (timespec_get(ts, TIME_UTC) == TIME_UTC) ? 0 : -1;
}

int npa_nanosleep(const struct timespec *req, struct timespec *rem) {
    (void)rem;
    DWORD ms = (DWORD)(req->tv_sec * 1000 + req->tv_nsec / 1000000);
    Sleep(ms);
    return 0;
}

#endif /* _WIN32 */
