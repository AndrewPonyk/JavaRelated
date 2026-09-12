/* SPDX-License-Identifier: MIT
 *
 * tests/fixtures/gen_sample.c — generate a deterministic sample.pcap.
 *
 * Standalone (no project headers) so the Makefile can build it with a plain
 * `cc`. Emits a small DLT_EN10MB capture exercising the decoders and several
 * anomaly rules: a normal SYN/ACK exchange, an XMAS scan, a NULL scan, a DNS
 * query, an ICMP echo, a 25-port TCP SYN scan (trips the port-scan heuristic),
 * and a truncated/malformed frame. All checksums are valid so the only alerts
 * are the intended ones.
 *
 * Usage: gen_sample [output.pcap]   (default: sample.pcap)
 */
#include <stdint.h>
#include <stdio.h>
#include <string.h>

typedef uint8_t  u8;
typedef uint16_t u16;
typedef uint32_t u32;

static FILE *g;
static u32   g_ts = 1700000000u;

static void w32(u32 v) { fwrite(&v, 4, 1, g); }
static void w16(u16 v) { fwrite(&v, 2, 1, g); }

/* RFC 1071 checksum over `len` bytes, seeded with `acc`. */
static u16 cksum(const u8 *d, u32 len, u32 acc) {
    u32 i = 0;
    for (; i + 1 < len; i += 2) acc += (u32)((d[i] << 8) | d[i + 1]);
    if (i < len) acc += (u32)(d[i] << 8);
    while (acc >> 16) acc = (acc & 0xffff) + (acc >> 16);
    return (u16)(~acc & 0xffff);
}

static void write_global_header(void) {
    w32(0xa1b2c3d4u);    /* magic (native endianness) */
    w16(2); w16(4);      /* version 2.4 */
    w32(0); w32(0);      /* thiszone, sigfigs */
    w32(65535);          /* snaplen */
    w32(1);              /* network = DLT_EN10MB */
}

static void write_pkt(const u8 *data, u32 len) {
    w32(g_ts++); w32(0);  /* ts_sec, ts_usec */
    w32(len);             /* incl_len */
    w32(len);             /* orig_len */
    fwrite(data, 1, len, g);
}

static void eth_hdr(u8 *b, u16 ethertype) {
    static const u8 dst[6] = {0x00,0x11,0x22,0x33,0x44,0x55};
    static const u8 src[6] = {0x66,0x77,0x88,0x99,0xaa,0xbb};
    memcpy(b, dst, 6);
    memcpy(b + 6, src, 6);
    b[12] = (u8)(ethertype >> 8);
    b[13] = (u8)ethertype;
}

static void ipv4_hdr(u8 *ip, u8 proto, u16 total_len, u32 sip, u32 dip) {
    memset(ip, 0, 20);
    ip[0] = 0x45;
    ip[2] = (u8)(total_len >> 8); ip[3] = (u8)total_len;
    ip[6] = 0x40;                  /* DF */
    ip[8] = 64;                    /* TTL */
    ip[9] = proto;
    ip[12] = (u8)(sip >> 24); ip[13] = (u8)(sip >> 16); ip[14] = (u8)(sip >> 8); ip[15] = (u8)sip;
    ip[16] = (u8)(dip >> 24); ip[17] = (u8)(dip >> 16); ip[18] = (u8)(dip >> 8); ip[19] = (u8)dip;
    u16 c = cksum(ip, 20, 0);
    ip[10] = (u8)(c >> 8); ip[11] = (u8)c;
}

/* Compute a TCP/UDP checksum (with IPv4 pseudo-header) into l4[cksum_off]. */
static void l4_cksum(const u8 *ip, u8 *l4, u16 l4len, int cksum_off) {
    u32 sip = (u32)(ip[12] << 24) | (u32)(ip[13] << 16) | (u32)(ip[14] << 8) | ip[15];
    u32 dip = (u32)(ip[16] << 24) | (u32)(ip[17] << 16) | (u32)(ip[18] << 8) | ip[19];
    u32 acc = (sip >> 16) + (sip & 0xffff) + (dip >> 16) + (dip & 0xffff) + ip[9] + l4len;
    l4[cksum_off] = 0; l4[cksum_off + 1] = 0;
    u16 c = cksum(l4, l4len, acc);
    l4[cksum_off] = (u8)(c >> 8); l4[cksum_off + 1] = (u8)c;
}

static u32 build_tcp(u8 *buf, u32 sip, u32 dip, u16 sp, u16 dp, u8 flags) {
    eth_hdr(buf, 0x0800);
    u8 *ip = buf + 14;
    ipv4_hdr(ip, 6, 40, sip, dip);
    u8 *tcp = buf + 34;
    memset(tcp, 0, 20);
    tcp[0] = (u8)(sp >> 8); tcp[1] = (u8)sp;
    tcp[2] = (u8)(dp >> 8); tcp[3] = (u8)dp;
    tcp[12] = 0x50;          /* data offset 5 words */
    tcp[13] = flags;
    tcp[14] = 0x72; tcp[15] = 0x10;   /* window */
    l4_cksum(ip, tcp, 20, 16);
    return 54;
}

static u32 build_udp(u8 *buf, u32 sip, u32 dip, u16 sp, u16 dp,
                     const u8 *payload, u16 plen) {
    eth_hdr(buf, 0x0800);
    u8 *ip = buf + 14;
    ipv4_hdr(ip, 17, (u16)(28 + plen), sip, dip);
    u8 *udp = buf + 34;
    u16 ulen = (u16)(8 + plen);
    udp[0] = (u8)(sp >> 8); udp[1] = (u8)sp;
    udp[2] = (u8)(dp >> 8); udp[3] = (u8)dp;
    udp[4] = (u8)(ulen >> 8); udp[5] = (u8)ulen;
    udp[6] = 0; udp[7] = 0;
    if (plen) memcpy(udp + 8, payload, plen);
    l4_cksum(ip, udp, ulen, 6);
    return 42u + plen;
}

static u32 build_icmp_echo(u8 *buf, u32 sip, u32 dip, u16 id, u16 seq) {
    eth_hdr(buf, 0x0800);
    u8 *ip = buf + 14;
    ipv4_hdr(ip, 1, 28, sip, dip);
    u8 *ic = buf + 34;
    ic[0] = 8; ic[1] = 0; ic[2] = 0; ic[3] = 0;     /* echo request */
    ic[4] = (u8)(id >> 8); ic[5] = (u8)id;
    ic[6] = (u8)(seq >> 8); ic[7] = (u8)seq;
    u16 c = cksum(ic, 8, 0);
    ic[2] = (u8)(c >> 8); ic[3] = (u8)c;
    return 42;
}

int main(int argc, char **argv) {
    const char *out = (argc > 1) ? argv[1] : "sample.pcap";
    g = fopen(out, "wb");
    if (!g) { perror("fopen"); return 1; }

    write_global_header();

    u8 buf[256];
    const u32 A = 0x0a000001u;   /* 10.0.0.1   */
    const u32 B = 0x0a000002u;   /* 10.0.0.2   */
    const u32 S = 0x0a0000c8u;   /* 10.0.0.200 (scanner) */

    write_pkt(buf, build_tcp(buf, A, B, 12345, 80, 0x02));   /* SYN  */
    write_pkt(buf, build_tcp(buf, B, A, 80, 12345, 0x12));   /* SYN-ACK */
    write_pkt(buf, build_tcp(buf, A, B, 12345, 80, 0x10));   /* ACK  */

    write_pkt(buf, build_tcp(buf, S, B, 40000, 80, 0x29));   /* XMAS (FIN+PSH+URG) */
    write_pkt(buf, build_tcp(buf, S, B, 40001, 80, 0x00));   /* NULL scan */

    static const u8 dns[] = {
        0x12,0x34, 0x01,0x00, 0,1, 0,0, 0,0, 0,0,
        3,'w','w','w', 7,'e','x','a','m','p','l','e', 3,'c','o','m', 0, 0,1, 0,1
    };
    write_pkt(buf, build_udp(buf, A, 0x08080808u, 53000, 53, dns, (u16)sizeof dns));

    write_pkt(buf, build_icmp_echo(buf, A, B, 0x1234, 1));

    /* 25 SYNs to distinct ports → trips the port-scan heuristic (>= 20). */
    for (u16 p = 1; p <= 25; ++p)
        write_pkt(buf, build_tcp(buf, S, B, 55000, p, 0x02));

    /* Truncated/malformed frame (not even a full Ethernet header). */
    u8 bad[10] = {0};
    write_pkt(bad, sizeof bad);

    fclose(g);
    fprintf(stderr, "gen_sample: wrote %s\n", out);
    return 0;
}
