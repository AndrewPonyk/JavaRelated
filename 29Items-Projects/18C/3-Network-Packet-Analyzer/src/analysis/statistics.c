/* SPDX-License-Identifier: MIT
 *
 * analysis/statistics.c — counter aggregation, rate derivation, top talkers.
 *
 * Top talkers use an open-addressing hash table (bounded probe with smallest-
 * in-window eviction), so per-packet cost stays O(probe) regardless of address
 * cardinality. The snapshot does a single pass to extract the top-N for the UI.
 */
#include "analysis/statistics.h"

#include <string.h>

#define TALKER_PROBE 32   /* max linear-probe distance before eviction */

void stats_init(statistics_t *s) {
    memset(s, 0, sizeof *s);
    pthread_mutex_init(&s->lock, NULL);
}

void stats_destroy(statistics_t *s) {
    if (s) pthread_mutex_destroy(&s->lock);
}

static talker_slot_t *talker_lookup(statistics_t *s, u32 addr) {
    const size_t mask = STATS_HASH_SIZE - 1;
    size_t h = (addr * 2654435761u) & mask;     /* Knuth multiplicative hash */

    for (size_t i = 0; i < TALKER_PROBE; ++i) {
        talker_slot_t *t = &s->talkers[(h + i) & mask];
        if (!t->used) {
            t->used = true; t->addr = addr; t->packets = 0; t->bytes = 0;
            s->talker_count++;
            return t;
        }
        if (t->addr == addr) return t;
    }
    /* Probe window full: evict the smallest-traffic slot in the window. */
    talker_slot_t *victim = &s->talkers[h & mask];
    for (size_t i = 1; i < TALKER_PROBE; ++i) {
        talker_slot_t *t = &s->talkers[(h + i) & mask];
        if (t->packets < victim->packets) victim = t;
    }
    victim->addr = addr; victim->packets = 0; victim->bytes = 0;
    return victim;
}

static void talker_record(statistics_t *s, u32 addr, u32 bytes) {
    if (addr == 0) return;
    talker_slot_t *t = talker_lookup(s, addr);
    t->packets++;
    t->bytes += bytes;
}

void stats_update(statistics_t *s, const decoded_packet_t *pkt) {
    pthread_mutex_lock(&s->lock);
    stats_view_t *v = &s->v;

    u32 bytes = pkt->frame ? pkt->frame->caplen : 0;
    v->total_packets++;
    v->total_bytes += bytes;
    if (pkt->partial) v->partial_packets++;

    switch (pkt->l3) {
        case L3_IPV4:  v->ipv4_packets++;  break;
        case L3_IPV6:  v->ipv6_packets++;  break;
        case L3_ARP:   v->arp_packets++;   break;
        default:       v->other_l3_packets++; break;
    }
    switch (pkt->l4) {
        case L4_TCP:   v->tcp_packets++;   break;
        case L4_UDP:   v->udp_packets++;   break;
        case L4_ICMP:  v->icmp_packets++;  break;
        case L4_NONE:  break;
        default:       v->other_l4_packets++; break;
    }

    if (pkt->l3 == L3_IPV4 && pkt->l3_span.present) {
        talker_record(s, pkt->ipv4.src, bytes);
        talker_record(s, pkt->ipv4.dst, bytes);
    }

    s->window_packets++;
    s->window_bytes += bytes;
    pthread_mutex_unlock(&s->lock);
}

void stats_add_alerts(statistics_t *s, u64 n) {
    pthread_mutex_lock(&s->lock);
    s->v.alerts += n;
    pthread_mutex_unlock(&s->lock);
}

void stats_tick(statistics_t *s, u64 now_ms) {
    pthread_mutex_lock(&s->lock);
    if (s->window_start_ms == 0) s->window_start_ms = now_ms;
    u64 elapsed = now_ms - s->window_start_ms;
    if (elapsed >= 1000) {
        double secs = (double)elapsed / 1000.0;
        s->v.pps = (double)s->window_packets / secs;
        s->v.bps = (double)s->window_bytes  / secs;
        s->window_start_ms = now_ms;
        s->window_packets  = 0;
        s->window_bytes    = 0;
    }
    pthread_mutex_unlock(&s->lock);
}

void stats_set_runtime(statistics_t *s, u64 dropped, u64 ring_used, u64 ring_cap) {
    pthread_mutex_lock(&s->lock);
    s->v.dropped       = dropped;
    s->v.ring_used     = ring_used;
    s->v.ring_capacity = ring_cap;
    pthread_mutex_unlock(&s->lock);
}

/* Insert (addr,packets,bytes) into a top-N array kept sorted descending. */
static void top_insert(talker_t *top, size_t n, u32 addr, u64 packets, u64 bytes) {
    if (packets <= top[n - 1].packets) return;     /* doesn't make the cut */
    size_t pos = n - 1;
    while (pos > 0 && top[pos - 1].packets < packets) {
        top[pos] = top[pos - 1];
        pos--;
    }
    top[pos].addr = addr; top[pos].packets = packets; top[pos].bytes = bytes;
}

void stats_snapshot(statistics_t *s, stats_view_t *out) {
    pthread_mutex_lock(&s->lock);
    memcpy(out, &s->v, sizeof *out);

    memset(out->talkers, 0, sizeof out->talkers);
    for (size_t i = 0; i < STATS_HASH_SIZE; ++i) {
        if (s->talkers[i].used) {
            top_insert(out->talkers, STATS_TOP_TALKERS,
                       s->talkers[i].addr, s->talkers[i].packets,
                       s->talkers[i].bytes);
        }
    }
    pthread_mutex_unlock(&s->lock);
}
