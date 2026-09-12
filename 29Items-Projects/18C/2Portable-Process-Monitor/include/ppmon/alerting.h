/*
 * alerting.h — Threshold rules and evaluation with hysteresis.
 *
 * Pure domain logic (no OS calls) so it is fully unit-testable. Rules fire when
 * a metric crosses a high watermark and clear only after it drops below a low
 * watermark, preventing alert flapping.
 */
#ifndef PPMON_ALERTING_H
#define PPMON_ALERTING_H

#include "ppmon/ppmon.h"
#include "ppmon/metrics.h"

typedef enum ppmon_metric_kind {
    PPMON_METRIC_CPU_PERCENT = 0,
    PPMON_METRIC_WORKING_SET_BYTES,
    PPMON_METRIC_PRIVATE_BYTES,
    PPMON_METRIC_READ_BPS,
    PPMON_METRIC_WRITE_BPS
} ppmon_metric_kind_t;

typedef struct ppmon_alert_rule {
    ppmon_metric_kind_t metric;
    double high; /* fire when value >= high            */
    double low;  /* clear when value <  low (hysteresis) */
    int active;  /* internal state: currently firing?  */
} ppmon_alert_rule_t;

typedef struct ppmon_alert_event {
    uint32_t pid;
    char image_name[PPMON_MAX_IMAGE_NAME];
    ppmon_metric_kind_t metric;
    double value;
    int raised; /* 1 = newly raised, 0 = cleared */
} ppmon_alert_event_t;

/*
 * Evaluate all `rules` against one process's metrics, emitting transition
 * events (raised/cleared) into `events`. Returns count via *out_count.
 * No allocation, no OS calls — safe to fuzz and unit-test.
 */
ppmon_status_t ppmon_alert_evaluate(ppmon_alert_rule_t *rules, size_t rule_count,
                                    const ppmon_proc_metrics_t *m, ppmon_alert_event_t *events,
                                    size_t events_cap, size_t *out_count);

/* Extract the comparable scalar for a given metric kind. */
double ppmon_alert_metric_value(const ppmon_proc_metrics_t *m, ppmon_metric_kind_t kind);

/* ---------------------------------------------------------------------------
 * Stateful per-process alert engine.
 *
 * ppmon_alert_evaluate above is a pure primitive that tracks state in a single
 * rule set. Applied across many processes it would thrash, because hysteresis
 * state is per-(process, rule). The engine maintains an independent copy of the
 * rule set for every live PID, evaluates a whole batch of process metrics in one
 * call, and evicts the state of processes that have disappeared (emitting a
 * cleared event for any alert that was still active).
 * ------------------------------------------------------------------------- */
typedef struct ppmon_alert_engine ppmon_alert_engine_t;

/* Create an engine seeded from `rules` (copied), tracking up to `capacity` PIDs. */
ppmon_status_t ppmon_alert_engine_create(const ppmon_alert_rule_t *rules, size_t rule_count,
                                         size_t capacity, ppmon_alert_engine_t **out);
void ppmon_alert_engine_destroy(ppmon_alert_engine_t *e);

/*
 * Evaluate one cycle's worth of process metrics. Transition events (raised /
 * cleared) are written into `events` (capped at events_cap; excess transitions
 * still update internal state). Returns the event count via *out_count.
 */
ppmon_status_t ppmon_alert_engine_update(ppmon_alert_engine_t *e,
                                         const ppmon_proc_metrics_t *rows, size_t row_count,
                                         ppmon_alert_event_t *events, size_t events_cap,
                                         size_t *out_count);

/* Human-readable name for a metric kind (for console alert lines). */
const char *ppmon_alert_metric_name(ppmon_metric_kind_t kind);

#endif /* PPMON_ALERTING_H */
