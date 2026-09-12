/* SPDX-License-Identifier: MIT
 *
 * analysis/anomaly.h — the rule-matching detection engine.
 *
 * Given a decoded packet, evaluate the active ruleset and emit zero or more
 * alerts. The engine also keeps lightweight per-source state for stateful
 * heuristics (e.g. port-scan: many distinct dst ports from one src in a window).
 */
#ifndef NPA_ANALYSIS_ANOMALY_H
#define NPA_ANALYSIS_ANOMALY_H

#include "analysis/patterns.h"
#include "common/packet.h"
#include "common/types.h"

#define ALERT_MSG_MAX 160

typedef struct {
    u64        ts_ms;             /* when the alert fired                     */
    severity_t severity;
    char       rule[RULE_NAME_MAX];
    char       message[ALERT_MSG_MAX];
    u32        src_ip;            /* offending source (IPv4, host order)      */
    u32        dst_ip;
    u16        src_port;
    u16        dst_port;
} alert_t;

/* Callback invoked once per fired alert (e.g. push into the UI alert ring). */
typedef void (*alert_sink_fn)(const alert_t *a, void *user);

typedef struct anomaly_engine anomaly_engine_t;   /* opaque */

/*
 * Create an engine over `rules` (borrowed; must outlive the engine). Alerts are
 * delivered to `sink(alert, user)`.
 */
npa_result_t anomaly_create(anomaly_engine_t **out, const ruleset_t *rules,
                            alert_sink_fn sink, void *user);

void anomaly_destroy(anomaly_engine_t *e);

/*
 * Evaluate one decoded packet at time `now_ms`. Fires matching rules via the
 * sink. Returns the number of alerts emitted for this packet.
 */
int anomaly_evaluate(anomaly_engine_t *e, const decoded_packet_t *pkt,
                     u64 now_ms);

#endif /* NPA_ANALYSIS_ANOMALY_H */
