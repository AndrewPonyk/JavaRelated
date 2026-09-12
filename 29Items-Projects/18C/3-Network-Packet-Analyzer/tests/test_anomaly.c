/* SPDX-License-Identifier: MIT
 *
 * tests/test_anomaly.c — rule engine tests (table-driven).
 *
 * Builds decoded_packet_t values directly (no capture/decode) and asserts the
 * right rules fire. Covers flag scans, low TTL, port-scan heuristic, byte
 * signatures, and bad-checksum detection.
 */
#include "test_common.h"

#include "analysis/anomaly.h"
#include "analysis/patterns.h"
#include "common/packet.h"

#include <string.h>

#define MAX_CAUGHT 32
static alert_t g_caught[MAX_CAUGHT];
static size_t  g_caught_n;

static void reset_sink(void) { g_caught_n = 0; memset(g_caught, 0, sizeof g_caught); }
static void capture_sink(const alert_t *a, void *user) {
    (void)user;
    if (g_caught_n < MAX_CAUGHT) g_caught[g_caught_n++] = *a;
}
static bool fired(const char *rule) {
    for (size_t i = 0; i < g_caught_n; ++i)
        if (strcmp(g_caught[i].rule, rule) == 0) return true;
    return false;
}

static captured_frame_t g_frame;

static decoded_packet_t mk_tcp(u8 flags, u8 ttl, u16 dport) {
    decoded_packet_t p = {0};
    p.l3 = L3_IPV4; p.l4 = L4_TCP;
    p.l3_span.present = true; p.l4_span.present = true;
    p.ipv4.ttl = ttl; p.ipv4.src = 0x0A000005u; p.ipv4.dst = 0x0A000001u;
    p.tcp.flags = flags; p.tcp.src_port = 40000; p.tcp.dst_port = dport;
    return p;
}

/* Packet whose payload (at offset 0) contains `payload`, dst port 21. */
static decoded_packet_t mk_payload(const char *payload) {
    memset(&g_frame, 0, sizeof g_frame);
    size_t n = strlen(payload);
    memcpy(g_frame.data, payload, n);
    g_frame.caplen = (u32)n;
    decoded_packet_t p = {0};
    p.frame = &g_frame;
    p.l3 = L3_IPV4; p.l4 = L4_TCP;
    p.l3_span.present = true; p.l4_span.present = true;
    p.payload_span.present = true; p.payload_span.offset = 0; p.payload_span.length = (u16)n;
    p.tcp.dst_port = 21; p.tcp.src_port = 50000;
    return p;
}

static void test_null_scan_fires(void) {
    ruleset_t rs; patterns_load_builtin(&rs);
    anomaly_engine_t *e = NULL; anomaly_create(&e, &rs, capture_sink, NULL);
    reset_sink();
    decoded_packet_t p = mk_tcp(0x00, 64, 80);
    anomaly_evaluate(e, &p, 1000);
    ASSERT_TRUE(fired("tcp-null-scan"));
    anomaly_destroy(e);
}

static void test_xmas_scan_fires(void) {
    ruleset_t rs; patterns_load_builtin(&rs);
    anomaly_engine_t *e = NULL; anomaly_create(&e, &rs, capture_sink, NULL);
    reset_sink();
    decoded_packet_t p = mk_tcp((u8)(TCP_FIN | TCP_PSH | TCP_URG), 64, 80);
    anomaly_evaluate(e, &p, 1000);
    ASSERT_TRUE(fired("tcp-xmas-scan"));
    anomaly_destroy(e);
}

static void test_normal_syn_quiet(void) {
    ruleset_t rs; patterns_load_builtin(&rs);
    anomaly_engine_t *e = NULL; anomaly_create(&e, &rs, capture_sink, NULL);
    reset_sink();
    decoded_packet_t p = mk_tcp(TCP_SYN, 64, 443);
    anomaly_evaluate(e, &p, 1000);
    ASSERT_FALSE(fired("tcp-null-scan"));
    ASSERT_FALSE(fired("tcp-xmas-scan"));
    ASSERT_FALSE(fired("tcp-syn-fin"));
    anomaly_destroy(e);
}

static void test_low_ttl_fires(void) {
    ruleset_t rs; patterns_load_builtin(&rs);
    anomaly_engine_t *e = NULL; anomaly_create(&e, &rs, capture_sink, NULL);
    reset_sink();
    decoded_packet_t p = mk_tcp(TCP_ACK, 2, 80);
    anomaly_evaluate(e, &p, 1000);
    ASSERT_TRUE(fired("ip-low-ttl"));
    anomaly_destroy(e);
}

static void test_port_scan_heuristic(void) {
    ruleset_t rs; patterns_load_builtin(&rs);
    anomaly_engine_t *e = NULL; anomaly_create(&e, &rs, capture_sink, NULL);
    reset_sink();
    for (u16 port = 1; port <= 25; ++port) {
        decoded_packet_t p = mk_tcp(TCP_SYN, 64, port);
        anomaly_evaluate(e, &p, 1000 + port);
    }
    ASSERT_TRUE(fired("port-scan"));
    anomaly_destroy(e);
}

static void test_byte_signature(void) {
    ruleset_t rs;
    rs.count = 0;
    rule_t *r = &rs.rules[rs.count++];
    memset(r, 0, sizeof *r);
    r->enabled = true; r->severity = SEV_HIGH; r->match = MATCH_BYTE_SIG;
    snprintf(r->name, sizeof r->name, "ftp-pass");
    snprintf(r->message, sizeof r->message, "cleartext password");
    memcpy(r->sig, "PASS ", 5); r->sig_len = 5;

    anomaly_engine_t *e = NULL; anomaly_create(&e, &rs, capture_sink, NULL);

    reset_sink();
    decoded_packet_t hit = mk_payload("PASS hunter2\r\n");
    anomaly_evaluate(e, &hit, 1000);
    ASSERT_TRUE(fired("ftp-pass"));

    reset_sink();
    decoded_packet_t miss = mk_payload("USER bob\r\n");
    anomaly_evaluate(e, &miss, 1000);
    ASSERT_FALSE(fired("ftp-pass"));

    anomaly_destroy(e);
}

static void test_bad_checksum_fires(void) {
    ruleset_t rs; patterns_load_builtin(&rs);
    anomaly_engine_t *e = NULL; anomaly_create(&e, &rs, capture_sink, NULL);
    reset_sink();
    decoded_packet_t p = mk_tcp(TCP_ACK, 64, 443);
    p.l4_checksum_checked = true; p.l4_checksum_ok = false;   /* bad */
    anomaly_evaluate(e, &p, 1000);
    ASSERT_TRUE(fired("bad-checksum"));

    reset_sink();
    decoded_packet_t ok = mk_tcp(TCP_ACK, 64, 443);
    ok.l4_checksum_checked = true; ok.l4_checksum_ok = true;  /* good */
    anomaly_evaluate(e, &ok, 1000);
    ASSERT_FALSE(fired("bad-checksum"));
    anomaly_destroy(e);
}

int main(void) {
    printf("anomaly tests:\n");
    RUN_TEST(test_null_scan_fires);
    RUN_TEST(test_xmas_scan_fires);
    RUN_TEST(test_normal_syn_quiet);
    RUN_TEST(test_low_ttl_fires);
    RUN_TEST(test_port_scan_heuristic);
    RUN_TEST(test_byte_signature);
    RUN_TEST(test_bad_checksum_fires);
    return test_summary("anomaly");
}
