/* SPDX-License-Identifier: MIT
 *
 * decode/decode.c — top-level dissection orchestration.
 *
 * Resolves the link layer (Ethernet / Linux-cooked / null / raw), walks
 * L3 → L4 → payload delegating each layer to its dissector, and verifies the
 * L4 checksum centrally (it needs both L3 and L4 context). Any layer returning
 * non-OK marks the packet partial and stops the walk gracefully.
 */
#include "decode/decode.h"

#include <string.h>

/* Selected EtherTypes / IP protocol numbers we normalize. */
#define ETHERTYPE_IPV4 0x0800u
#define ETHERTYPE_IPV6 0x86DDu
#define ETHERTYPE_ARP  0x0806u

#define IPPROTO_ICMP_   1
#define IPPROTO_TCP_    6
#define IPPROTO_UDP_   17
#define IPPROTO_ICMP6_ 58

/* ---- RFC 1071 Internet checksum ---------------------------------------- */

u32 npa_cksum_accumulate(const u8 *data, u32 len, u32 acc) {
    u32 i = 0;
    for (; i + 1 < len; i += 2) {
        acc += (u32)((u32)data[i] << 8 | (u32)data[i + 1]);
    }
    if (i < len) {                 /* odd trailing byte → high half of a word */
        acc += (u32)((u32)data[i] << 8);
    }
    return acc;
}

u16 npa_cksum_fold(u32 acc) {
    while (acc >> 16) {
        acc = (acc & 0xFFFFu) + (acc >> 16);
    }
    return (u16)(~acc & 0xFFFFu);
}

/*
 * Verify the transport checksum once L3+L4 are parsed. Sets
 * out->l4_checksum_{checked,ok}. Skips (leaves unchecked) when the segment is
 * truncated, fragmented, or — for UDP — the sender left the checksum zero.
 */
static void verify_l4_checksum(decoded_packet_t *out, const u8 *buf, u32 caplen) {
    if (!out->l4_span.present) return;

    const u32 l4_off = out->l4_span.offset;
    u32 l3_end = 0;
    u8  proto  = 0;
    bool has_pseudo;   /* TCP/UDP/ICMPv6 use a pseudo-header; ICMPv4 does not */

    if (out->l3 == L3_IPV4) {
        if (out->ipv4.frag_offset != 0 || out->ipv4.more_fragments) return;
        if (out->ipv4.total_len < out->ipv4.ihl_bytes) return;
        l3_end = (u32)out->l3_span.offset + out->ipv4.total_len;
        proto  = out->ipv4.protocol;
    } else if (out->l3 == L3_IPV6) {
        l3_end = (u32)out->l3_span.offset + 40u + out->ipv6.payload_len;
        proto  = out->ipv6.next_header;
    } else {
        return;
    }

    if (l3_end <= l4_off) return;
    const u32 l4len = l3_end - l4_off;
    if (l4_off > caplen || l4len > caplen - l4_off) return;  /* truncated */

    has_pseudo = (out->l4 == L4_TCP || out->l4 == L4_UDP || out->l4 == L4_ICMPV6);

    u32 acc = 0;
    if (has_pseudo) {
        if (out->l3 == L3_IPV4) {
            acc += (out->ipv4.src >> 16) + (out->ipv4.src & 0xFFFFu);
            acc += (out->ipv4.dst >> 16) + (out->ipv4.dst & 0xFFFFu);
            acc += proto;
            acc += (l4len & 0xFFFFu);
        } else { /* IPv6 pseudo-header */
            acc = npa_cksum_accumulate(out->ipv6.src, 16, acc);
            acc = npa_cksum_accumulate(out->ipv6.dst, 16, acc);
            acc += (l4len >> 16) + (l4len & 0xFFFFu);
            acc += proto;
        }
    } else if (out->l4 != L4_ICMP) {
        return;   /* nothing else carries a checksum we verify */
    }

    /* UDP checksum of 0 means "not computed" (legal over IPv4) → don't judge. */
    if (out->l4 == L4_UDP) {
        if (l4len < 8) return;
        if (rd_be16(buf, l4_off + 6) == 0) return;
    }

    acc = npa_cksum_accumulate(&buf[l4_off], l4len, acc);
    out->l4_checksum_checked = true;
    out->l4_checksum_ok = (npa_cksum_fold(acc) == 0);
}

/*
 * Resolve the link layer into (ethertype, next L3 offset). Normalizes
 * non-Ethernet datalinks to an EtherType so the L3 switch is uniform.
 */
static npa_result_t decode_link(const captured_frame_t *frame, decoded_packet_t *out,
                                u32 *next_off, u16 *ethertype) {
    const u8 *buf = frame->data;
    const u32 len = frame->caplen;

    switch (frame->datalink) {
        case NPA_DLT_EN10MB:
            return decode_ethernet(buf, len, 0, out, next_off, ethertype);

        case NPA_DLT_LINUX_SLL: {       /* 16-byte cooked header; type @ 14 */
            if (!in_bounds(len, 0, 16)) return NPA_ERR_TRUNCATED;
            *ethertype = rd_be16(buf, 14);
            *next_off  = 16;
            out->l2_span = (layer_span_t){ .offset = 0, .length = 16, .present = true };
            return NPA_OK;
        }
        case NPA_DLT_NULL: {            /* 4-byte host-order address family */
            if (!in_bounds(len, 0, 4)) return NPA_ERR_TRUNCATED;
            u32 fam = (u32)buf[0] | ((u32)buf[1] << 8) |
                      ((u32)buf[2] << 16) | ((u32)buf[3] << 24);
            *ethertype = (fam == 2) ? ETHERTYPE_IPV4
                       : (fam == 24 || fam == 28 || fam == 30) ? ETHERTYPE_IPV6 : 0;
            *next_off  = 4;
            out->l2_span = (layer_span_t){ .offset = 0, .length = 4, .present = true };
            return NPA_OK;
        }
        case NPA_DLT_RAW: {             /* bare IP; sniff the version nibble */
            if (!in_bounds(len, 0, 1)) return NPA_ERR_TRUNCATED;
            u8 v = (u8)(buf[0] >> 4);
            *ethertype = (v == 4) ? ETHERTYPE_IPV4 : (v == 6) ? ETHERTYPE_IPV6 : 0;
            *next_off  = 0;
            return NPA_OK;
        }
        default:                        /* unknown link → assume Ethernet */
            return decode_ethernet(buf, len, 0, out, next_off, ethertype);
    }
}

npa_result_t decode_frame(const captured_frame_t *frame, decoded_packet_t *out) {
    if (!frame || !out) return NPA_ERR_INVAL;

    *out = (decoded_packet_t){0};   /* all spans default to .present = false */
    out->frame = frame;

    const u8 *buf = frame->data;
    const u32 len = frame->caplen;

    /* ---- L2 / link layer ---- */
    u32 next = 0;
    u16 ethertype = 0;
    if (decode_link(frame, out, &next, &ethertype) != NPA_OK) {
        out->partial = true;
        return NPA_OK;
    }

    /* ---- L3 ---- */
    u8 l4proto = 0;
    switch (ethertype) {
        case ETHERTYPE_IPV4:
            out->l3 = L3_IPV4;
            if (decode_ipv4(buf, len, next, out, &next, &l4proto) != NPA_OK) {
                out->partial = true;
                return NPA_OK;
            }
            break;
        case ETHERTYPE_IPV6:
            out->l3 = L3_IPV6;
            if (decode_ipv6(buf, len, next, out, &next, &l4proto) != NPA_OK) {
                out->partial = true;
                return NPA_OK;
            }
            break;
        case ETHERTYPE_ARP:
            out->l3 = L3_ARP;
            return NPA_OK;                 /* ARP has no L4 in our model */
        default:
            out->l3 = L3_OTHER;
            return NPA_OK;
    }

    /* ---- L4 ---- */
    bool have_payload = false;
    switch (l4proto) {
        case IPPROTO_TCP_:
            out->l4 = L4_TCP;
            if (decode_tcp(buf, len, next, out, &next) != NPA_OK) {
                out->partial = true;
                return NPA_OK;
            }
            have_payload = true;
            break;
        case IPPROTO_UDP_:
            out->l4 = L4_UDP;
            if (decode_udp(buf, len, next, out, &next) != NPA_OK) {
                out->partial = true;
                return NPA_OK;
            }
            have_payload = true;
            break;
        case IPPROTO_ICMP_:
            out->l4 = L4_ICMP;
            if (decode_icmp(buf, len, next, out) != NPA_OK) out->partial = true;
            break;
        case IPPROTO_ICMP6_:
            out->l4 = L4_ICMPV6;
            if (decode_icmp(buf, len, next, out) != NPA_OK) out->partial = true;
            break;
        default:
            out->l4 = L4_OTHER;
            return NPA_OK;
    }

    /* ---- Application payload (TCP/UDP only) + checksum ---- */
    if (have_payload && next < len) {
        out->payload_span.offset  = (u16)next;
        out->payload_span.length  = (u16)(len - next);
        out->payload_span.present = true;
    }
    verify_l4_checksum(out, buf, len);
    return NPA_OK;
}
