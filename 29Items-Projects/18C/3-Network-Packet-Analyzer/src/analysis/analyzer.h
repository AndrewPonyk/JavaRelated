/* SPDX-License-Identifier: MIT
 *
 * analysis/analyzer.h — the consumer worker thread.
 *
 * Drains the ring buffer and, per frame, runs: decode → stats → anomaly →
 * publish-to-UI. Decoupled from the UI via callbacks so it can run headless.
 */
#ifndef NPA_ANALYSIS_ANALYZER_H
#define NPA_ANALYSIS_ANALYZER_H

#include <pthread.h>
#include <stdatomic.h>

#include "analysis/anomaly.h"
#include "analysis/statistics.h"
#include "buffer/ring_buffer.h"
#include "common/types.h"

/* Called for each decoded packet (e.g. UI appends a row). May be NULL. */
typedef void (*packet_sink_fn)(const decoded_packet_t *pkt, void *user);

typedef struct {
    ring_buffer_t    *ring;       /* source of frames                         */
    statistics_t     *stats;      /* counters to update                       */
    anomaly_engine_t *anomaly;    /* detector to run                          */
    packet_sink_fn    on_packet;  /* UI publish hook (nullable)               */
    void             *user;       /* passed to on_packet                      */
    long              count;      /* stop after N packets (0 = unlimited)     */
} analyzer_cfg_t;

typedef struct analyzer analyzer_t;   /* opaque */

/* Create an analyzer bound to its inputs (does not start the thread). */
npa_result_t analyzer_create(analyzer_t **out, const analyzer_cfg_t *cfg);

/* Spawn the worker thread. */
npa_result_t analyzer_start(analyzer_t *a);

/* True once the worker hit its --count limit and stopped the pipeline. */
bool analyzer_done(const analyzer_t *a);

/* Total packets the worker has decoded so far. */
u64 analyzer_processed(const analyzer_t *a);

/* Signal stop, wait for the worker to drain+exit, and join. Idempotent. */
void analyzer_stop(analyzer_t *a);

/* Free the analyzer. Call after analyzer_stop(). */
void analyzer_destroy(analyzer_t *a);

#endif /* NPA_ANALYSIS_ANALYZER_H */
