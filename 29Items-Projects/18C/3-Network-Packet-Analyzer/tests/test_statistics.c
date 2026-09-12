/* SPDX-License-Identifier: MIT
 *
 * tests/test_statistics.c — counters, rates, top talkers, runtime mirroring.
 */
#include "test_common.h"

#include "analysis/statistics.h"

#include <string.h>

/* Build a decoded IPv4 packet backed by the caller's frame (dst=0 so only the
 * source is counted as a talker). The caller must keep `f` alive until the
 * matching stats_update() call. */
static decoded_packet_t mk(captured_frame_t *f, u32 src, u32 caplen, l4_proto_t l4) {
    memset(f, 0, sizeof *f);
    f->caplen = caplen;
    decoded_packet_t p = {0};
    p.frame = f;
    p.l3 = L3_IPV4;
    p.l3_span.present = true;
    p.ipv4.src = src;
    p.ipv4.dst = 0;
    p.l4 = l4;
    return p;
}

static void test_counters(void) {
    statistics_t s;
    stats_init(&s);
    captured_frame_t f;
    decoded_packet_t p;
    /* Update immediately after each build so each reads its own caplen. */
    p = mk(&f, 0x0A000001u, 100, L4_TCP);  stats_update(&s, &p);
    p = mk(&f, 0x0A000001u, 100, L4_TCP);  stats_update(&s, &p);
    p = mk(&f, 0x0A000002u, 60,  L4_UDP);  stats_update(&s, &p);
    p = mk(&f, 0x0A000003u, 40,  L4_ICMP); stats_update(&s, &p);

    stats_view_t v;
    stats_snapshot(&s, &v);
    ASSERT_EQ_UINT(4u, v.total_packets);
    ASSERT_EQ_UINT(300u, v.total_bytes);
    ASSERT_EQ_UINT(2u, v.tcp_packets);
    ASSERT_EQ_UINT(1u, v.udp_packets);
    ASSERT_EQ_UINT(1u, v.icmp_packets);
    ASSERT_EQ_UINT(4u, v.ipv4_packets);
}

static void test_top_talkers(void) {
    statistics_t s;
    stats_init(&s);
    captured_frame_t f;
    decoded_packet_t p;
    for (int i = 0; i < 3; ++i) { p = mk(&f, 0xAAAAAAAAu, 10, L4_TCP); stats_update(&s, &p); }
    for (int i = 0; i < 7; ++i) { p = mk(&f, 0xBBBBBBBBu, 10, L4_TCP); stats_update(&s, &p); }
    p = mk(&f, 0xCCCCCCCCu, 10, L4_TCP); stats_update(&s, &p);

    stats_view_t v;
    stats_snapshot(&s, &v);
    ASSERT_EQ_UINT(0xBBBBBBBBu, v.talkers[0].addr);
    ASSERT_EQ_UINT(7u, v.talkers[0].packets);
    ASSERT_EQ_UINT(0xAAAAAAAAu, v.talkers[1].addr);
    ASSERT_EQ_UINT(3u, v.talkers[1].packets);
}

static void test_alerts_and_runtime(void) {
    statistics_t s;
    stats_init(&s);
    stats_add_alerts(&s, 3);
    stats_set_runtime(&s, 5, 10, 8192);
    stats_view_t v;
    stats_snapshot(&s, &v);
    ASSERT_EQ_UINT(3u, v.alerts);
    ASSERT_EQ_UINT(5u, v.dropped);
    ASSERT_EQ_UINT(10u, v.ring_used);
    ASSERT_EQ_UINT(8192u, v.ring_capacity);
}

static void test_rate_window(void) {
    statistics_t s;
    stats_init(&s);
    captured_frame_t f;
    decoded_packet_t p;
    for (int i = 0; i < 10; ++i) { p = mk(&f, 0x01020304u, 100, L4_UDP); stats_update(&s, &p); }
    stats_tick(&s, 1000);     /* establishes window_start */
    stats_tick(&s, 2000);     /* 1s later → pps/bps computed */
    stats_view_t v;
    stats_snapshot(&s, &v);
    ASSERT_TRUE(v.pps >= 9.0 && v.pps <= 11.0);
    ASSERT_TRUE(v.bps >= 900.0 && v.bps <= 1100.0);
}

int main(void) {
    printf("statistics tests:\n");
    RUN_TEST(test_counters);
    RUN_TEST(test_top_talkers);
    RUN_TEST(test_alerts_and_runtime);
    RUN_TEST(test_rate_window);
    return test_summary("statistics");
}
