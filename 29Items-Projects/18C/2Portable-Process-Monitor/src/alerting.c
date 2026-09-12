/* alerting.c — threshold evaluation with hysteresis (pure primitive) plus a
 * stateful per-process engine layered on top of it. */
#include <stdlib.h>
#include <string.h>
#include "ppmon/alerting.h"

double ppmon_alert_metric_value(const ppmon_proc_metrics_t *m, ppmon_metric_kind_t kind) {
    switch (kind) {
    case PPMON_METRIC_CPU_PERCENT:
        return m->cpu_percent;
    case PPMON_METRIC_WORKING_SET_BYTES:
        return (double)m->working_set_bytes;
    case PPMON_METRIC_PRIVATE_BYTES:
        return (double)m->private_bytes;
    case PPMON_METRIC_READ_BPS:
        return (double)m->read_bytes_per_sec;
    case PPMON_METRIC_WRITE_BPS:
        return (double)m->write_bytes_per_sec;
    default:
        return 0.0;
    }
}

const char *ppmon_alert_metric_name(ppmon_metric_kind_t kind) {
    switch (kind) {
    case PPMON_METRIC_CPU_PERCENT:
        return "CPU%";
    case PPMON_METRIC_WORKING_SET_BYTES:
        return "WorkingSet";
    case PPMON_METRIC_PRIVATE_BYTES:
        return "PrivateBytes";
    case PPMON_METRIC_READ_BPS:
        return "ReadB/s";
    case PPMON_METRIC_WRITE_BPS:
        return "WriteB/s";
    default:
        return "?";
    }
}

/* Emit one event into the buffer if space remains; state still advances. */
static void emit(ppmon_alert_event_t *events, size_t cap, size_t *n,
                 const ppmon_proc_metrics_t *m, ppmon_metric_kind_t metric, double value,
                 int raised) {
    if (*n >= cap) return;
    ppmon_alert_event_t *e = &events[(*n)++];
    e->pid                 = m->pid;
    e->metric              = metric;
    e->value               = value;
    e->raised              = raised;
    strncpy(e->image_name, m->image_name, sizeof(e->image_name) - 1);
    e->image_name[sizeof(e->image_name) - 1] = '\0';
}

ppmon_status_t ppmon_alert_evaluate(ppmon_alert_rule_t *rules, size_t rule_count,
                                    const ppmon_proc_metrics_t *m, ppmon_alert_event_t *events,
                                    size_t events_cap, size_t *out_count) {
    if (!rules || !m || !events || !out_count) return PPMON_ERR_INVALID_ARG;
    size_t n = 0;

    for (size_t i = 0; i < rule_count; ++i) {
        ppmon_alert_rule_t *r = &rules[i];
        double v              = ppmon_alert_metric_value(m, r->metric);

        /* Hysteresis: fire on >= high, clear only on < low. Between the two
         * watermarks the previous state is held, preventing flapping. */
        if (!r->active && v >= r->high) {
            r->active = 1;
            emit(events, events_cap, &n, m, r->metric, v, 1);
        } else if (r->active && v < r->low) {
            r->active = 0;
            emit(events, events_cap, &n, m, r->metric, v, 0);
        }
    }

    *out_count = n;
    return PPMON_OK;
}

/* --------------------------------------------------------------------------- */
/* Stateful per-process engine.                                                */
/* --------------------------------------------------------------------------- */

typedef struct ae_entry {
    int in_use;
    uint32_t pid;
    uint64_t last_gen;
    char image_name[PPMON_MAX_IMAGE_NAME];
    ppmon_alert_rule_t *rules; /* this PID's own copy of the rule set */
} ae_entry_t;

struct ppmon_alert_engine {
    ppmon_alert_rule_t *templ; /* pristine template, copied per PID */
    size_t rule_count;
    ae_entry_t *entries;
    size_t capacity;
    uint64_t gen;
};

ppmon_status_t ppmon_alert_engine_create(const ppmon_alert_rule_t *rules, size_t rule_count,
                                         size_t capacity, ppmon_alert_engine_t **out) {
    if (!out || !rules || rule_count == 0 || capacity == 0) return PPMON_ERR_INVALID_ARG;

    ppmon_alert_engine_t *e = calloc(1, sizeof(*e));
    if (!e) return PPMON_ERR_NO_MEMORY;

    e->rule_count = rule_count;
    e->capacity   = capacity;
    e->templ      = malloc(rule_count * sizeof(*e->templ));
    e->entries    = calloc(capacity, sizeof(*e->entries));
    if (!e->templ || !e->entries) {
        ppmon_alert_engine_destroy(e);
        return PPMON_ERR_NO_MEMORY;
    }
    memcpy(e->templ, rules, rule_count * sizeof(*e->templ));
    for (size_t i = 0; i < rule_count; ++i)
        e->templ[i].active = 0;

    for (size_t i = 0; i < capacity; ++i) {
        e->entries[i].rules = malloc(rule_count * sizeof(ppmon_alert_rule_t));
        if (!e->entries[i].rules) {
            ppmon_alert_engine_destroy(e);
            return PPMON_ERR_NO_MEMORY;
        }
    }
    *out = e;
    return PPMON_OK;
}

void ppmon_alert_engine_destroy(ppmon_alert_engine_t *e) {
    if (!e) return;
    if (e->entries) {
        for (size_t i = 0; i < e->capacity; ++i)
            free(e->entries[i].rules);
        free(e->entries);
    }
    free(e->templ);
    free(e);
}

static ae_entry_t *engine_find(ppmon_alert_engine_t *e, uint32_t pid) {
    for (size_t i = 0; i < e->capacity; ++i)
        if (e->entries[i].in_use && e->entries[i].pid == pid) return &e->entries[i];
    return NULL;
}

static ae_entry_t *engine_acquire(ppmon_alert_engine_t *e) {
    ae_entry_t *stale = NULL;
    for (size_t i = 0; i < e->capacity; ++i) {
        ae_entry_t *en = &e->entries[i];
        if (!en->in_use) return en;
        if (!stale && en->last_gen != e->gen) stale = en;
    }
    return stale;
}

ppmon_status_t ppmon_alert_engine_update(ppmon_alert_engine_t *e,
                                         const ppmon_proc_metrics_t *rows, size_t row_count,
                                         ppmon_alert_event_t *events, size_t events_cap,
                                         size_t *out_count) {
    if (!e || (!rows && row_count) || !events || !out_count) return PPMON_ERR_INVALID_ARG;
    e->gen++;
    size_t n = 0;

    for (size_t i = 0; i < row_count; ++i) {
        const ppmon_proc_metrics_t *m = &rows[i];
        ae_entry_t *en                = engine_find(e, m->pid);
        if (!en) {
            en = engine_acquire(e);
            if (!en) continue; /* engine saturated; skip this PID this cycle */
            en->in_use = 1;
            en->pid    = m->pid;
            memcpy(en->rules, e->templ, e->rule_count * sizeof(ppmon_alert_rule_t));
        }
        en->last_gen = e->gen;
        strncpy(en->image_name, m->image_name, sizeof(en->image_name) - 1);
        en->image_name[sizeof(en->image_name) - 1] = '\0';

        size_t k = 0;
        ppmon_alert_evaluate(en->rules, e->rule_count, m, events + n, events_cap - n, &k);
        n += k;
    }

    /* Evict entries for processes that disappeared, emitting a cleared event for
     * any alert that was still active so the console sees a clean resolution. */
    for (size_t i = 0; i < e->capacity; ++i) {
        ae_entry_t *en = &e->entries[i];
        if (!en->in_use || en->last_gen == e->gen) continue;
        for (size_t r = 0; r < e->rule_count; ++r) {
            if (en->rules[r].active && n < events_cap) {
                ppmon_alert_event_t *ev = &events[n++];
                ev->pid                 = en->pid;
                ev->metric              = en->rules[r].metric;
                ev->value               = 0.0;
                ev->raised              = 0;
                strncpy(ev->image_name, en->image_name, sizeof(ev->image_name) - 1);
                ev->image_name[sizeof(ev->image_name) - 1] = '\0';
            }
        }
        en->in_use = 0;
    }

    *out_count = n;
    return PPMON_OK;
}
