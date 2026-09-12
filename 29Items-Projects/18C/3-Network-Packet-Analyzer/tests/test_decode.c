/* SPDX-License-Identifier: MIT
 *
 * tests/test_decode.c — dissector tests over crafted byte buffers.
 *
 * Covers Ethernet/VLAN/Q-in-Q, IPv4 (+checksum +fragmentation), IPv6, TCP
 * (+options), UDP, ICMP, and the most important class for a parser: malformed
 * and truncated inputs that must be rejected cleanly (run under ASan in CI).
 */
#include "test_common.h"

#include "common/packet.h"
#include "decode/decode.h"

#include <string.h>

/* Wrap raw bytes as an Ethernet (DLT_EN10MB) frame. */
static captured_frame_t frame_of(const u8 *bytes, u32 len) {
    captured_frame_t f = {0};
    f.caplen   = len;
    f.wirelen  = len;
    f.datalink = NPA_DLT_EN10MB;
    memcpy(f.data, bytes, len);
    return f;
}

/* Ethernet + IPv4 + TCP SYN, 10.0.0.2:12345 -> 10.0.0.1:80. */
static const u8 SAMPLE_TCP_SYN[] = {
    0x00,0x11,0x22,0x33,0x44,0x55, 0x66,0x77,0x88,0x99,0xAA,0xBB, 0x08,0x00,
    0x45,0x00,0x00,0x28, 0x00,0x00,0x40,0x00, 0x40,0x06,0x00,0x00,
    0x0A,0x00,0x00,0x02, 0x0A,0x00,0x00,0x01,
    0x30,0x39, 0x00,0x50, 0x00,0x00,0x00,0x01, 0x00,0x00,0x00,0x00,
    0x50,0x02, 0x72,0x10, 0x00,0x00, 0x00,0x00,
};

static void test_decode_full_tcp_syn(void) {
    captured_frame_t f = frame_of(SAMPLE_TCP_SYN, sizeof SAMPLE_TCP_SYN);
    decoded_packet_t p;
    ASSERT_EQ_INT(NPA_OK, decode_frame(&f, &p));
    ASSERT_FALSE(p.partial);
    ASSERT_EQ_INT(L3_IPV4, p.l3);
    ASSERT_EQ_INT(L4_TCP,  p.l4);
    ASSERT_EQ_UINT(0x66, p.eth.src[0]);
    ASSERT_EQ_UINT(0x0800u, p.eth.ethertype);
    ASSERT_EQ_UINT(64u, p.ipv4.ttl);
    ASSERT_EQ_UINT(6u,  p.ipv4.protocol);
    ASSERT_EQ_UINT(0x0A000002u, p.ipv4.src);
    ASSERT_EQ_UINT(12345u, p.tcp.src_port);
    ASSERT_EQ_UINT(80u, p.tcp.dst_port);
    ASSERT_EQ_UINT(TCP_SYN, p.tcp.flags);
}

static void test_decode_truncated_ethernet(void) {
    const u8 tiny[] = { 0x00, 0x11, 0x22 };
    captured_frame_t f = frame_of(tiny, sizeof tiny);
    decoded_packet_t p;
    ASSERT_EQ_INT(NPA_OK, decode_frame(&f, &p));
    ASSERT_TRUE(p.partial);
    ASSERT_FALSE(p.l2_span.present);
}

static void test_decode_truncated_ipv4(void) {
    u8 buf[18];
    memcpy(buf, SAMPLE_TCP_SYN, 14);
    buf[14] = 0x45; buf[15] = 0x00; buf[16] = 0x00; buf[17] = 0x28;
    captured_frame_t f = frame_of(buf, sizeof buf);
    decoded_packet_t p;
    ASSERT_EQ_INT(NPA_OK, decode_frame(&f, &p));
    ASSERT_TRUE(p.partial);
    ASSERT_TRUE(p.l2_span.present);
    ASSERT_FALSE(p.l3_span.present);
}

static void test_decode_bad_ip_version(void) {
    u8 buf[34];
    memset(buf, 0, sizeof buf);
    memcpy(buf, SAMPLE_TCP_SYN, 14);
    buf[14] = 0x65;     /* version 6 nibble in an IPv4 slot */
    captured_frame_t f = frame_of(buf, sizeof buf);
    decoded_packet_t p;
    ASSERT_EQ_INT(NPA_OK, decode_frame(&f, &p));
    ASSERT_TRUE(p.partial);
}

static void test_bounds_helpers(void) {
    ASSERT_TRUE(in_bounds(20, 0, 20));
    ASSERT_TRUE(in_bounds(20, 6, 14));
    ASSERT_FALSE(in_bounds(20, 6, 15));
    ASSERT_FALSE(in_bounds(20, 21, 1));
    ASSERT_FALSE(in_bounds(4, 0xFFFFFFFFu, 1));
}

/* ---- VLAN / Q-in-Q ----------------------------------------------------- */

static void test_vlan_single_tag(void) {
    u8 buf[58];
    memset(buf, 0, sizeof buf);
    memcpy(buf, SAMPLE_TCP_SYN, 12);          /* MACs */
    buf[12] = 0x81; buf[13] = 0x00;           /* 802.1Q TPID */
    buf[14] = 0x00; buf[15] = 0x64;           /* TCI: VLAN 100 */
    buf[16] = 0x08; buf[17] = 0x00;           /* inner type IPv4 */
    memcpy(&buf[18], &SAMPLE_TCP_SYN[14], sizeof SAMPLE_TCP_SYN - 14);
    captured_frame_t f = frame_of(buf, sizeof buf);
    decoded_packet_t p;
    ASSERT_EQ_INT(NPA_OK, decode_frame(&f, &p));
    ASSERT_TRUE(p.eth.has_vlan);
    ASSERT_EQ_UINT(100u, p.eth.vlan_id);
    ASSERT_EQ_UINT(1u, p.eth.vlan_count);
    ASSERT_EQ_INT(L3_IPV4, p.l3);
    ASSERT_EQ_INT(L4_TCP,  p.l4);
}

static void test_vlan_qinq(void) {
    u8 buf[62];
    memset(buf, 0, sizeof buf);
    memcpy(buf, SAMPLE_TCP_SYN, 12);
    buf[12] = 0x88; buf[13] = 0xA8;           /* outer S-tag */
    buf[14] = 0x00; buf[15] = 0x64;           /* VLAN 100 */
    buf[16] = 0x81; buf[17] = 0x00;           /* inner C-tag */
    buf[18] = 0x00; buf[19] = 0xC8;           /* VLAN 200 */
    buf[20] = 0x08; buf[21] = 0x00;           /* IPv4 */
    memcpy(&buf[22], &SAMPLE_TCP_SYN[14], sizeof SAMPLE_TCP_SYN - 14);
    captured_frame_t f = frame_of(buf, sizeof buf);
    decoded_packet_t p;
    ASSERT_EQ_INT(NPA_OK, decode_frame(&f, &p));
    ASSERT_EQ_UINT(2u, p.eth.vlan_count);
    ASSERT_EQ_UINT(100u, p.eth.vlan_id);      /* outermost */
    ASSERT_EQ_INT(L3_IPV4, p.l3);
}

/* ---- IPv6 -------------------------------------------------------------- */

static void test_decode_ipv6_tcp(void) {
    u8 buf[74];
    memset(buf, 0, sizeof buf);
    memcpy(buf, SAMPLE_TCP_SYN, 12);
    buf[12] = 0x86; buf[13] = 0xDD;           /* IPv6 */
    buf[14] = 0x60;                            /* version 6 */
    buf[18] = 0x00; buf[19] = 0x14;            /* payload_len = 20 */
    buf[20] = 0x06;                            /* next header = TCP */
    buf[21] = 0x40;                            /* hop limit = 64 */
    buf[22] = 0x20; buf[23] = 0x01; buf[24] = 0x0d; buf[25] = 0xb8;  /* src 2001:db8::1 */
    buf[37] = 0x01;
    buf[38] = 0x20; buf[39] = 0x01; buf[40] = 0x0d; buf[41] = 0xb8;  /* dst 2001:db8::2 */
    buf[53] = 0x02;
    /* TCP at offset 54 */
    buf[54] = 0x12; buf[55] = 0x34;            /* src port 0x1234 */
    buf[56] = 0x00; buf[57] = 0x50;            /* dst port 80 */
    buf[66] = 0x50; buf[67] = 0x02;            /* data offset 20, SYN */
    captured_frame_t f = frame_of(buf, sizeof buf);
    decoded_packet_t p;
    ASSERT_EQ_INT(NPA_OK, decode_frame(&f, &p));
    ASSERT_EQ_INT(L3_IPV6, p.l3);
    ASSERT_EQ_INT(L4_TCP,  p.l4);
    ASSERT_EQ_UINT(64u, p.ipv6.hop_limit);
    ASSERT_EQ_UINT(6u,  p.ipv6.next_header);
    ASSERT_EQ_UINT(0x1234u, p.tcp.src_port);
    ASSERT_EQ_UINT(80u, p.tcp.dst_port);
    ASSERT_EQ_UINT(0x20, p.ipv6.src[0]);
}

/* ---- Checksums --------------------------------------------------------- */

/* Canonical valid IPv4 header (RFC/Wikipedia example, checksum 0xb861). */
static const u8 IPV4_HDR_GOOD[20] = {
    0x45,0x00,0x00,0x73, 0x00,0x00,0x40,0x00, 0x40,0x11,0xb8,0x61,
    0xc0,0xa8,0x00,0x01, 0xc0,0xa8,0x00,0xc7,
};

static void test_internet_checksum_fn(void) {
    /* A buffer including a correct checksum folds to 0. */
    u32 acc = npa_cksum_accumulate(IPV4_HDR_GOOD, 20, 0);
    ASSERT_EQ_UINT(0u, npa_cksum_fold(acc));
    /* Corrupt one byte → no longer 0. */
    u8 bad[20];
    memcpy(bad, IPV4_HDR_GOOD, 20);
    bad[8] ^= 0xFF;
    ASSERT_TRUE(npa_cksum_fold(npa_cksum_accumulate(bad, 20, 0)) != 0);
}

static void test_ipv4_checksum_valid(void) {
    u8 buf[34];
    memcpy(buf, SAMPLE_TCP_SYN, 14);          /* ethernet */
    memcpy(&buf[14], IPV4_HDR_GOOD, 20);
    captured_frame_t f = frame_of(buf, sizeof buf);
    decoded_packet_t p;
    decode_frame(&f, &p);
    ASSERT_TRUE(p.l3_checksum_checked);
    ASSERT_TRUE(p.l3_checksum_ok);

    buf[14 + 8] ^= 0xFF;                       /* corrupt TTL, leave checksum */
    f = frame_of(buf, sizeof buf);
    decode_frame(&f, &p);
    ASSERT_TRUE(p.l3_checksum_checked);
    ASSERT_FALSE(p.l3_checksum_ok);
}

static void test_udp_checksum_disabled_not_checked(void) {
    u8 buf[42];
    memset(buf, 0, sizeof buf);
    memcpy(buf, SAMPLE_TCP_SYN, 14);
    /* IPv4: ihl5, total_len=28 (20+8), proto=17 UDP, no fragmentation. */
    buf[14] = 0x45; buf[16] = 0x00; buf[17] = 0x1C;
    buf[22] = 0x40; buf[23] = 0x11;
    buf[26] = 0x0A; buf[29] = 0x01;            /* some src/dst */
    buf[30] = 0x0A; buf[33] = 0x02;
    /* UDP at 34: ports + len=8 + checksum=0 (disabled). */
    buf[34] = 0x00; buf[35] = 0x35;            /* sport 53 */
    buf[36] = 0x00; buf[37] = 0x35;            /* dport 53 */
    buf[38] = 0x00; buf[39] = 0x08;            /* len 8 */
    buf[40] = 0x00; buf[41] = 0x00;            /* checksum 0 → disabled */
    captured_frame_t f = frame_of(buf, sizeof buf);
    decoded_packet_t p;
    decode_frame(&f, &p);
    ASSERT_EQ_INT(L4_UDP, p.l4);
    ASSERT_FALSE(p.l4_checksum_checked);       /* zero checksum → not judged */
}

/* ---- Fragmentation + TCP options --------------------------------------- */

static void test_ipv4_fragmentation_flags(void) {
    u8 buf[34];
    memset(buf, 0, sizeof buf);
    memcpy(buf, SAMPLE_TCP_SYN, 14);
    buf[14] = 0x45; buf[17] = 0x14;            /* total_len 20 */
    buf[22] = 0x40; buf[23] = 0xFD;            /* proto 253 (L4_OTHER) */
    /* flags/frag at bytes 6-7 of IP header (buf 20-21): MF=1, offset=1 (×8). */
    buf[20] = 0x20; buf[21] = 0x01;
    captured_frame_t f = frame_of(buf, sizeof buf);
    decoded_packet_t p;
    decode_frame(&f, &p);
    ASSERT_EQ_INT(L3_IPV4, p.l3);
    ASSERT_TRUE(p.ipv4.more_fragments);
    ASSERT_FALSE(p.ipv4.dont_fragment);
    ASSERT_EQ_UINT(8u, p.ipv4.frag_offset);    /* 1 × 8 */
}

static void test_tcp_options_mss(void) {
    u8 buf[58];
    memset(buf, 0, sizeof buf);
    memcpy(buf, SAMPLE_TCP_SYN, 14);
    buf[14] = 0x45; buf[16] = 0x00; buf[17] = 0x2C;  /* total_len 44 */
    buf[22] = 0x40; buf[23] = 0x06;            /* ttl, proto TCP */
    /* TCP at 34: ports, data offset = 6 words (24 bytes), SYN. */
    buf[34] = 0x04; buf[35] = 0xD2;            /* sport 1234 */
    buf[36] = 0x00; buf[37] = 0x50;            /* dport 80 */
    buf[46] = 0x60; buf[47] = 0x02;            /* data offset 24, SYN */
    /* Option region (off 54..57): MSS = 1460 (0x05B4). */
    buf[54] = 0x02; buf[55] = 0x04; buf[56] = 0x05; buf[57] = 0xB4;
    captured_frame_t f = frame_of(buf, sizeof buf);
    decoded_packet_t p;
    decode_frame(&f, &p);
    ASSERT_EQ_INT(L4_TCP, p.l4);
    ASSERT_EQ_UINT(24u, p.tcp.data_off_bytes);
    ASSERT_TRUE(p.tcp.has_mss);
    ASSERT_EQ_UINT(1460u, p.tcp.mss);
}

int main(void) {
    printf("decode tests:\n");
    RUN_TEST(test_decode_full_tcp_syn);
    RUN_TEST(test_decode_truncated_ethernet);
    RUN_TEST(test_decode_truncated_ipv4);
    RUN_TEST(test_decode_bad_ip_version);
    RUN_TEST(test_bounds_helpers);
    RUN_TEST(test_vlan_single_tag);
    RUN_TEST(test_vlan_qinq);
    RUN_TEST(test_decode_ipv6_tcp);
    RUN_TEST(test_internet_checksum_fn);
    RUN_TEST(test_ipv4_checksum_valid);
    RUN_TEST(test_udp_checksum_disabled_not_checked);
    RUN_TEST(test_ipv4_fragmentation_flags);
    RUN_TEST(test_tcp_options_mss);
    return test_summary("decode");
}
