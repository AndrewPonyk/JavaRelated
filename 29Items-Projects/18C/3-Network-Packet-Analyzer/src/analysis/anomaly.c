/* SPDX-License-Identifier: MIT
 *
 * analysis/anomaly.c — stateless rule matching + a stateful port-scan heuristic.
 *
 * Stateless rules (flags/ttl/port/byte-sig/malformed) are evaluated directly.
 * The port-scan heuristic keeps a tiny bounded per-source table counting
 * distinct destination ports within a sliding window.
 */
#include "analysis/anomaly.h"

#include <stdio.h>    /* snprintf */
#include <stdlib.h>
#include <string.h>   /* memcmp  */

#define SCAN_TABLE_SIZE   1024   /* bounded source tracker (Phase 3: hash+LRU) */
#define SCAN_PORT_THRESH  20     /* distinct dst ports → "scan"               */
#define SCAN_WINDOW_MS    5000

typedef struct {
    u32 src;
    u32 distinct_ports;     /* approximate count (catches sequential scans)   */
    u16 last_port;
    u64 window_start_ms;
    bool used;
} scan_entry_t;

struct anomaly_engine {
    const ruleset_t *rules;
    alert_sink_fn    sink;
    void            *user;
    scan_entry_t     scan[SCAN_TABLE_SIZE];
};

npa_result_t anomaly_create(anomaly_engine_t **out, const ruleset_t *rules,
                            alert_sink_fn sink, void *user) {
    if (!out || !rules || !sink) return NPA_ERR_INVAL;
    anomaly_engine_t *e = calloc(1, sizeof *e);
    if (!e) return NPA_ERR_NOMEM;
    e->rules = rules;
    e->sink  = sink;
    e->user  = user;
    *out = e;
    return NPA_OK;
}

void anomaly_destroy(anomaly_engine_t *e) { free(e); }

/* Fill the addressing fields of an alert from the decoded packet. */
static void fill_endpoints(alert_t *a, const decoded_packet_t *p) {
    if (p->l3 == L3_IPV4) { a->src_ip = p->ipv4.src; a->dst_ip = p->ipv4.dst; }
    if (p->l4 == L4_TCP) { a->src_port = p->tcp.src_port; a->dst_port = p->tcp.dst_port; }
    else if (p->l4 == L4_UDP) { a->src_port = p->udp.src_port; a->dst_port = p->udp.dst_port; }
}

/* Does payload contain the rule's byte signature? Bounds-checked. */
static bool payload_has_sig(const decoded_packet_t *p, const rule_t *r) {
    if (!p->payload_span.present || r->sig_len == 0) return false;
    const u8 *buf = p->frame->data;
    u32 start = p->payload_span.offset;
    u32 plen  = p->payload_span.length;
    if (r->sig_len > plen) return false;
    for (u32 i = 0; i + r->sig_len <= plen; ++i) {
        if (memcmp(&buf[start + i], r->sig, r->sig_len) == 0) return true;
    }
    return false;
}

/* Evaluate a single stateless rule against the packet. */
static bool rule_matches(const rule_t *r, const decoded_packet_t *p) {
    if (!r->enabled || r->match == MATCH_NONE) return false;

    if (r->match & MATCH_MALFORMED) {
        if (!p->partial) return false;
    }
    if (r->match & MATCH_TCP_FLAGS) {
        if (p->l4 != L4_TCP) return false;
        if ((p->tcp.flags & r->tcp_flag_mask) != r->tcp_flag_value) return false;
    }
    if (r->match & MATCH_IP_TTL_LT) {
        if (p->l3 != L3_IPV4 || p->ipv4.ttl >= r->ttl) return false;
    }
    if (r->match & MATCH_L4_PORT) {
        u16 sp = 0, dp = 0;
        if (p->l4 == L4_TCP) { sp = p->tcp.src_port; dp = p->tcp.dst_port; }
        else if (p->l4 == L4_UDP) { sp = p->udp.src_port; dp = p->udp.dst_port; }
        if (sp != r->port && dp != r->port) return false;
    }
    if (r->match & MATCH_BYTE_SIG) {
        if (!payload_has_sig(p, r)) return false;
    }
    if (r->match & MATCH_BAD_CHECKSUM) {
        bool bad = (p->l3_checksum_checked && !p->l3_checksum_ok) ||
                   (p->l4_checksum_checked && !p->l4_checksum_ok);
        if (!bad) return false;
    }
    return true;
}

/* Stateful port-scan heuristic; returns true (and fills *a) when it trips. */
static bool detect_port_scan(anomaly_engine_t *e, const decoded_packet_t *p,
                             u64 now_ms, alert_t *a) {
    if (p->l3 != L3_IPV4 || p->l4 != L4_TCP) return false;
    if (!(p->tcp.flags & TCP_SYN)) return false;   /* count SYN probes */

    u32 src = p->ipv4.src;
    size_t idx = (src * 2654435761u) % SCAN_TABLE_SIZE;  /* Knuth hash */
    scan_entry_t *s = &e->scan[idx];

    if (!s->used || s->src != src || (now_ms - s->window_start_ms) > SCAN_WINDOW_MS) {
        s->used = true; s->src = src; s->distinct_ports = 0;
        s->last_port = 0; s->window_start_ms = now_ms;
    }
    if (p->tcp.dst_port != s->last_port) {      /* cheap distinct-ish counter */
        s->distinct_ports++;
        s->last_port = p->tcp.dst_port;
    }
    if (s->distinct_ports == SCAN_PORT_THRESH) {  /* fire once per window */
        a->severity = SEV_HIGH;
        snprintf(a->rule, sizeof a->rule, "port-scan");
        snprintf(a->message, sizeof a->message,
                 "Possible TCP port scan: %u+ ports in %dms",
                 SCAN_PORT_THRESH, SCAN_WINDOW_MS);
        fill_endpoints(a, p);
        return true;
    }
    return false;
}

int anomaly_evaluate(anomaly_engine_t *e, const decoded_packet_t *pkt,
                     u64 now_ms) {
    if (!e || !pkt) return 0;
    int fired = 0;

    for (size_t i = 0; i < e->rules->count; ++i) {
        const rule_t *r = &e->rules->rules[i];
        if (!rule_matches(r, pkt)) continue;
        alert_t a = { .ts_ms = now_ms, .severity = r->severity };
        snprintf(a.rule, sizeof a.rule, "%s", r->name);
        snprintf(a.message, sizeof a.message, "%s", r->message);
        fill_endpoints(&a, pkt);
        e->sink(&a, e->user);
        fired++;
    }

    alert_t scan = { .ts_ms = now_ms };
    if (detect_port_scan(e, pkt, now_ms, &scan)) {
        e->sink(&scan, e->user);
        fired++;
    }
    return fired;
}
