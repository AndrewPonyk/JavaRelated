/* SPDX-License-Identifier: MIT
 *
 * analysis/analyzer.c — consumer worker: ring → decode → stats → detect → UI.
 *
 * Drains the ring, decodes each frame, updates stats (incl. mirrored ring
 * health), runs the anomaly engine, publishes to the UI sink, and honors an
 * optional --count limit (closing the ring to stop the producer cleanly).
 */
#include "analysis/analyzer.h"

#include "decode/decode.h"
#include "util/log.h"

#include <stdlib.h>
#include <time.h>

struct analyzer {
    analyzer_cfg_t cfg;
    pthread_t      thread;
    atomic_bool    started;
    bool           joined;
    atomic_bool    running;
    atomic_bool    done;       /* hit --count limit                          */
    atomic_uint_fast64_t processed;
};

static u64 now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (u64)ts.tv_sec * 1000u + (u64)(ts.tv_nsec / 1000000L);
}

static void mirror_ring_health(analyzer_t *a, u64 t) {
    if (!a->cfg.stats) return;
    stats_tick(a->cfg.stats, t);
    stats_set_runtime(a->cfg.stats,
                      ring_dropped(a->cfg.ring),
                      (u64)ring_size(a->cfg.ring),
                      (u64)ring_capacity(a->cfg.ring));
}

static void *analyzer_main(void *arg) {
    analyzer_t *a = arg;
    captured_frame_t *frame = malloc(sizeof *frame);   /* 64 KiB: off the stack */
    if (!frame) {
        LOG_F("analyzer: OOM allocating frame scratch");
        atomic_store(&a->running, false);
        return NULL;
    }
    decoded_packet_t decoded;
    u64  last_tick = now_ms();
    long count     = a->cfg.count;

    LOG_I("analyzer: worker started (count limit=%ld)", count);
    for (;;) {
        npa_result_t r = ring_pop(a->cfg.ring, frame);
        if (r == NPA_ERR_AGAIN) break;     /* ring closed + drained → exit */
        if (r != NPA_OK) continue;

        if (decode_frame(frame, &decoded) != NPA_OK) continue;

        u64 t = now_ms();
        if (a->cfg.stats) stats_update(a->cfg.stats, &decoded);
        if (a->cfg.anomaly) {
            int fired = anomaly_evaluate(a->cfg.anomaly, &decoded, t);
            if (fired > 0 && a->cfg.stats) stats_add_alerts(a->cfg.stats, (u64)fired);
        }
        if (a->cfg.on_packet) a->cfg.on_packet(&decoded, a->cfg.user);

        u64 n = atomic_fetch_add_explicit(&a->processed, 1, memory_order_relaxed) + 1;

        if (t - last_tick >= 1000) { mirror_ring_health(a, t); last_tick = t; }

        if (count > 0 && (long)n >= count) {
            LOG_I("analyzer: reached count limit (%ld); stopping", count);
            atomic_store(&a->done, true);
            ring_close(a->cfg.ring);       /* stop the producer cleanly */
            break;
        }
    }

    if (a->cfg.stats) mirror_ring_health(a, now_ms());
    free(frame);
    atomic_store(&a->running, false);
    LOG_I("analyzer: worker exiting (%llu packets)",
          (unsigned long long)atomic_load(&a->processed));
    return NULL;
}

npa_result_t analyzer_create(analyzer_t **out, const analyzer_cfg_t *cfg) {
    if (!out || !cfg || !cfg->ring) return NPA_ERR_INVAL;
    analyzer_t *a = calloc(1, sizeof *a);
    if (!a) return NPA_ERR_NOMEM;
    a->cfg    = *cfg;
    a->joined = false;
    atomic_init(&a->started, false);
    atomic_init(&a->running, false);
    atomic_init(&a->done, false);
    atomic_init(&a->processed, 0);
    *out = a;
    return NPA_OK;
}

npa_result_t analyzer_start(analyzer_t *a) {
    if (!a) return NPA_ERR_INVAL;
    atomic_store(&a->running, true);
    if (pthread_create(&a->thread, NULL, analyzer_main, a) != 0) {
        atomic_store(&a->running, false);
        return NPA_ERR_INTERNAL;
    }
    atomic_store(&a->started, true);
    return NPA_OK;
}

bool analyzer_done(const analyzer_t *a) {
    return a && atomic_load((atomic_bool *)&a->done);
}

u64 analyzer_processed(const analyzer_t *a) {
    return a ? atomic_load((atomic_uint_fast64_t *)&a->processed) : 0;
}

void analyzer_stop(analyzer_t *a) {
    if (!a) return;
    ring_close(a->cfg.ring);
    if (atomic_load(&a->started) && !a->joined) {
        pthread_join(a->thread, NULL);
        a->joined = true;
    }
}

void analyzer_destroy(analyzer_t *a) { free(a); }
