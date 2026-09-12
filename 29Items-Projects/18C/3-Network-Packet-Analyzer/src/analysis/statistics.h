/* SPDX-License-Identifier: MIT
 *
 * analysis/statistics.h — rolling capture counters + top talkers.
 *
 * Updated on the analyzer thread (one writer); read by the UI thread via a
 * snapshot copy under the stats lock. Top talkers are tracked in an internal
 * open-addressing hash table; the snapshot computes the top-N for display.
 */
#ifndef NPA_ANALYSIS_STATISTICS_H
#define NPA_ANALYSIS_STATISTICS_H

#include <pthread.h>

#include "common/packet.h"
#include "common/types.h"

#define STATS_TOP_TALKERS 16
#define STATS_HASH_SIZE  4096   /* power of two; internal talker table        */

typedef struct {
    u32 addr;        /* IPv4, host order (0 = empty slot)                     */
    u64 packets;
    u64 bytes;
} talker_t;

/* Internal hash-table slot (not part of the public snapshot). */
typedef struct {
    u32  addr;
    u64  packets;
    u64  bytes;
    bool used;
} talker_slot_t;

typedef struct {
    u64 total_packets;
    u64 total_bytes;
    u64 partial_packets;     /* decode produced a partial result             */
    u64 alerts;              /* anomaly alerts raised so far                  */

    /* L3 mix */
    u64 ipv4_packets;
    u64 ipv6_packets;
    u64 arp_packets;
    u64 other_l3_packets;

    /* L4 mix */
    u64 tcp_packets;
    u64 udp_packets;
    u64 icmp_packets;
    u64 other_l4_packets;

    /* Derived rates (recomputed on tick) */
    double pps;              /* packets/sec over the last window              */
    double bps;              /* bytes/sec over the last window                */

    /* Pipeline health (mirrored from the ring each tick). */
    u64 dropped;
    u64 ring_used;
    u64 ring_capacity;

    /* Top talkers (computed into here at snapshot time, sorted desc). */
    talker_t talkers[STATS_TOP_TALKERS];
} stats_view_t;

typedef struct {
    stats_view_t    v;
    pthread_mutex_t lock;     /* guards all access (single writer + UI reads) */
    u64             window_start_ms;
    u64             window_packets;
    u64             window_bytes;
    talker_slot_t   talkers[STATS_HASH_SIZE];
    size_t          talker_count;
} statistics_t;

/* Initialize counters to zero. */
void stats_init(statistics_t *s);

/* Release resources held by `s` (the stats mutex). Pairs with stats_init. */
void stats_destroy(statistics_t *s);

/* Update counters from one decoded packet (analyzer thread only). */
void stats_update(statistics_t *s, const decoded_packet_t *pkt);

/* Record that `n` anomaly alerts fired (analyzer thread). */
void stats_add_alerts(statistics_t *s, u64 n);

/* Recompute pps/bps using `now_ms`; call ~once/sec from the analyzer. */
void stats_tick(statistics_t *s, u64 now_ms);

/* Mirror ring health into the stats (analyzer thread, ~once/sec). */
void stats_set_runtime(statistics_t *s, u64 dropped, u64 ring_used, u64 ring_cap);

/* Copy a consistent snapshot for the UI, computing top talkers (takes lock). */
void stats_snapshot(statistics_t *s, stats_view_t *out);

#endif /* NPA_ANALYSIS_STATISTICS_H */
