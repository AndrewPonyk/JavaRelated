/* timer.c — drift-free QueryPerformanceCounter scheduling. */
#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#endif
#include "ppmon/timer.h"

ppmon_status_t ppmon_timer_init(ppmon_timer_t *t, uint32_t interval_ms) {
    if (!t || interval_ms == 0) return PPMON_ERR_INVALID_ARG;
#if defined(_WIN32)
    LARGE_INTEGER freq, now;
    if (!QueryPerformanceFrequency(&freq) || !QueryPerformanceCounter(&now))
        return PPMON_ERR_OS;
    t->qpc_frequency  = freq.QuadPart;
    t->anchor_ticks   = now.QuadPart;
    t->interval_ticks = (int64_t)((freq.QuadPart * (int64_t)interval_ms) / 1000);
    t->tick_index     = 0;
    return PPMON_OK;
#else
    (void)interval_ms;
    return PPMON_ERR_UNSUPPORTED; /* QPC scheduling is Windows-only by design */
#endif
}

int64_t ppmon_timer_now(const ppmon_timer_t *t) {
    (void)t;
#if defined(_WIN32)
    LARGE_INTEGER now;
    QueryPerformanceCounter(&now);
    return now.QuadPart;
#else
    return 0;
#endif
}

ppmon_status_t ppmon_timer_wait_next(ppmon_timer_t *t) {
    if (!t) return PPMON_ERR_INVALID_ARG;
#if defined(_WIN32)
    t->tick_index++;
    /* Target is computed against the absolute anchor, so latency never
     * accumulates: a late cycle simply shortens the next wait (no drift). We
     * coarse-Sleep until within ~1ms of the boundary, then busy-spin the
     * remainder for sub-millisecond alignment without burning a full core. */
    int64_t target = t->anchor_ticks + (int64_t)t->tick_index * t->interval_ticks;
    for (;;) {
        int64_t now = ppmon_timer_now(t);
        if (now >= target) break;
        int64_t remaining_ms =
            ((target - now) * 1000) / (t->qpc_frequency ? t->qpc_frequency : 1);
        if (remaining_ms > 1) Sleep((DWORD)(remaining_ms - 1));
    }
    return PPMON_OK;
#else
    return PPMON_ERR_UNSUPPORTED;
#endif
}

double ppmon_timer_ticks_to_seconds(const ppmon_timer_t *t, int64_t ticks) {
    if (!t || t->qpc_frequency == 0) return 0.0;
    return (double)ticks / (double)t->qpc_frequency;
}
