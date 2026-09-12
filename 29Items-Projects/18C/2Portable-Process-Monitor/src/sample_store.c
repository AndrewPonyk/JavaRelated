/* sample_store.c — per-process slot tracking + delta-based metric derivation.
 *
 * Each tracked process owns a slot keyed by (pid, start_time) so that PID reuse
 * starts a fresh history. A generation counter marks which slots were committed
 * in the current cycle; derive() emits those and evicts the rest (exited procs),
 * keeping memory bounded to the live process set. */
#include <stdlib.h>
#include <string.h>
#include "ppmon/sample_store.h"

typedef struct slot {
    int in_use;
    uint32_t pid;
    uint64_t start_time;
    ppmon_sample_t prev;
    ppmon_sample_t cur;
    int have_prev;
    uint64_t last_gen;
} slot_t;

struct ppmon_store {
    slot_t *slots;
    size_t capacity;
    size_t history; /* reserved for deeper history (Phase 3) */
    uint64_t gen;
};

ppmon_status_t ppmon_store_create(size_t capacity, size_t history, ppmon_store_t **out) {
    if (!out || capacity == 0) return PPMON_ERR_INVALID_ARG;
    ppmon_store_t *s = calloc(1, sizeof(*s));
    if (!s) return PPMON_ERR_NO_MEMORY;
    s->slots = calloc(capacity, sizeof(slot_t));
    if (!s->slots) {
        free(s);
        return PPMON_ERR_NO_MEMORY;
    }
    s->capacity = capacity;
    s->history  = history;
    s->gen      = 0;
    *out        = s;
    return PPMON_OK;
}

void ppmon_store_destroy(ppmon_store_t *s) {
    if (!s) return;
    free(s->slots);
    free(s);
}

void ppmon_store_begin_cycle(ppmon_store_t *s) {
    if (s) s->gen++;
}

/* Find the slot for (pid, start_time), or NULL. */
static slot_t *find_slot(ppmon_store_t *s, uint32_t pid, uint64_t start_time) {
    for (size_t i = 0; i < s->capacity; ++i) {
        slot_t *sl = &s->slots[i];
        if (sl->in_use && sl->pid == pid && sl->start_time == start_time) return sl;
    }
    return NULL;
}

/* Find a free slot, or one whose process was not seen this cycle (evictable). */
static slot_t *acquire_slot(ppmon_store_t *s) {
    slot_t *stale = NULL;
    for (size_t i = 0; i < s->capacity; ++i) {
        slot_t *sl = &s->slots[i];
        if (!sl->in_use) return sl;
        if (!stale && sl->last_gen != s->gen) stale = sl;
    }
    return stale; /* may be NULL if the store is full of live processes */
}

ppmon_status_t ppmon_store_commit(ppmon_store_t *s, const ppmon_sample_t *sample) {
    if (!s || !sample) return PPMON_ERR_INVALID_ARG;

    slot_t *sl = find_slot(s, sample->pid, sample->start_time_100ns);
    if (sl) {
        sl->prev      = sl->cur; /* shift current -> previous for delta math */
        sl->cur       = *sample;
        sl->have_prev = 1;
        sl->last_gen  = s->gen;
        return PPMON_OK;
    }

    sl = acquire_slot(s);
    if (!sl) return PPMON_ERR_NO_MEMORY; /* store saturated with live processes */

    memset(sl, 0, sizeof(*sl));
    sl->in_use     = 1;
    sl->pid        = sample->pid;
    sl->start_time = sample->start_time_100ns;
    sl->cur        = *sample;
    sl->have_prev  = 0;
    sl->last_gen   = s->gen;
    return PPMON_OK;
}

/* Derive presentation metrics for one (prev,cur) pair. */
static void derive_one(const slot_t *sl, int64_t qpc_freq, unsigned cpu_count,
                       ppmon_proc_metrics_t *m) {
    memset(m, 0, sizeof(*m));
    m->pid = sl->cur.pid;
    memcpy(m->image_name, sl->cur.image_name, sizeof(m->image_name));
    m->working_set_bytes = sl->cur.working_set_bytes;
    m->private_bytes     = sl->cur.private_bytes;

    if (!sl->have_prev || qpc_freq == 0) return;

    int64_t wall_ticks = (int64_t)(sl->cur.captured_qpc - sl->prev.captured_qpc);
    double wall_sec    = (double)wall_ticks / (double)qpc_freq;
    if (wall_sec <= 0.0) return;

    /* CPU time deltas are 100ns units -> seconds = delta * 1e-7. Divide by the
     * core count so the result is 0..100 across all cores (Task-Manager style). */
    uint64_t dk    = sl->cur.kernel_time_100ns - sl->prev.kernel_time_100ns;
    uint64_t du    = sl->cur.user_time_100ns - sl->prev.user_time_100ns;
    double cpu_sec = (double)(dk + du) * 1e-7;
    unsigned cores = cpu_count ? cpu_count : 1;
    double pct     = (cpu_sec / wall_sec) * 100.0 / (double)cores;
    if (pct < 0.0) pct = 0.0;     /* guard against counter wrap */
    if (pct > 100.0) pct = 100.0; /* clamp scheduling jitter */
    m->cpu_percent = pct;

    /* I/O counters are monotonic per process, but guard against a regression
     * (would otherwise wrap the unsigned subtraction into a bogus huge rate). */
    uint64_t dr            = sl->cur.read_bytes >= sl->prev.read_bytes
                                 ? sl->cur.read_bytes - sl->prev.read_bytes
                                 : 0;
    uint64_t dw            = sl->cur.write_bytes >= sl->prev.write_bytes
                                 ? sl->cur.write_bytes - sl->prev.write_bytes
                                 : 0;
    m->read_bytes_per_sec  = (uint64_t)((double)dr / wall_sec);
    m->write_bytes_per_sec = (uint64_t)((double)dw / wall_sec);
}

ppmon_status_t ppmon_store_derive(ppmon_store_t *s, int64_t qpc_frequency, unsigned cpu_count,
                                  ppmon_proc_metrics_t *out, size_t out_cap,
                                  size_t *out_count) {
    if (!s || !out || !out_count) return PPMON_ERR_INVALID_ARG;
    size_t n = 0;
    for (size_t i = 0; i < s->capacity; ++i) {
        slot_t *sl = &s->slots[i];
        if (!sl->in_use) continue;
        if (sl->last_gen != s->gen) {
            sl->in_use = 0; /* process not seen this cycle -> evict (it exited) */
            continue;
        }
        if (n < out_cap) derive_one(sl, qpc_frequency, cpu_count, &out[n++]);
    }
    *out_count = n;
    return PPMON_OK;
}
