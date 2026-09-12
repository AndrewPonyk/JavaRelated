/*
 * sample_store.h — Ring buffer of raw samples + delta-based metric derivation.
 *
 * Holds the previous and current snapshot per tracked process (keyed on
 * PID + start-time to survive PID reuse) and computes derived metrics such as
 * CPU percent and per-second I/O rates.
 */
#ifndef PPMON_SAMPLE_STORE_H
#define PPMON_SAMPLE_STORE_H

#include "ppmon/ppmon.h"
#include "ppmon/metrics.h"

typedef struct ppmon_store ppmon_store_t;

/* Create a store tracking up to `capacity` processes with `history` depth. */
ppmon_status_t ppmon_store_create(size_t capacity, size_t history, ppmon_store_t **out);
void ppmon_store_destroy(ppmon_store_t *s);

/*
 * Begin a new poll cycle. Must be called once before the batch of commits for
 * that cycle; it advances the internal generation so that derive() can evict
 * the slots of processes that were not seen this cycle (i.e. that have exited).
 */
void ppmon_store_begin_cycle(ppmon_store_t *s);

/*
 * Commit a freshly captured raw sample. The store matches it to the prior
 * sample for the same (pid, start_time); a start_time mismatch is treated as
 * PID reuse and starts a fresh history in a new slot.
 */
ppmon_status_t ppmon_store_commit(ppmon_store_t *s, const ppmon_sample_t *sample);

/*
 * Compute derived metrics for the current cycle into a caller buffer, and evict
 * slots not committed this cycle. `qpc_frequency` and the wallclock delta drive
 * the CPU% / rate math:
 *   cpu% = (Δkernel + Δuser) / (Δwallclock * cpu_count) * 100
 * Returns the number of rows written via *out_count.
 */
ppmon_status_t ppmon_store_derive(ppmon_store_t *s, int64_t qpc_frequency, unsigned cpu_count,
                                  ppmon_proc_metrics_t *out, size_t out_cap,
                                  size_t *out_count);

#endif /* PPMON_SAMPLE_STORE_H */
