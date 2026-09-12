/*
 * timer.h — Drift-free polling clock built on QueryPerformanceCounter.
 *
 * The interval scheduler computes each wake time against an absolute QPC anchor
 * so sampling does not accumulate latency under load.
 */
#ifndef PPMON_TIMER_H
#define PPMON_TIMER_H

#include "ppmon/ppmon.h"

typedef struct ppmon_timer {
    int64_t qpc_frequency;  /* ticks per second (QueryPerformanceFrequency) */
    int64_t anchor_ticks;   /* QPC value at loop start                      */
    int64_t interval_ticks; /* configured interval expressed in QPC ticks   */
    uint64_t tick_index;    /* number of intervals elapsed since anchor     */
} ppmon_timer_t;

/* Initialise the timer with a polling interval in milliseconds. */
ppmon_status_t ppmon_timer_init(ppmon_timer_t *t, uint32_t interval_ms);

/* Current high-resolution counter value. */
int64_t ppmon_timer_now(const ppmon_timer_t *t);

/*
 * Block until the next aligned tick boundary, then advance the index.
 * Computes the sleep against the absolute anchor to avoid drift.
 *
 * TODO: combine a coarse Sleep() with a short spin near the boundary for
 *       sub-millisecond alignment without burning a full core.
 */
ppmon_status_t ppmon_timer_wait_next(ppmon_timer_t *t);

/* Convert a QPC tick delta to seconds (double). */
double ppmon_timer_ticks_to_seconds(const ppmon_timer_t *t, int64_t ticks);

#endif /* PPMON_TIMER_H */
