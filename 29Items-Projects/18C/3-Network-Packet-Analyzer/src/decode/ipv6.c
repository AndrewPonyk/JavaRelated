/* SPDX-License-Identifier: MIT
 *
 * decode/ipv6.c — IPv6 base header + extension-header walk.
 *
 * Parses the fixed 40-byte header, then follows the Next Header chain through
 * hop-by-hop / routing / fragment / destination-options extension headers
 * until it reaches an upper-layer protocol (TCP/UDP/ICMPv6/…) or runs out of
 * bytes. Bounded iteration count prevents a crafted ext-header loop from
 * spinning. Every read is bounds-checked.
 */
#include "decode/decode.h"

#include <string.h>

#define IPV6_HDR_LEN 40u
#define IPV6_MAX_EXT  8u   /* cap ext-header chain length (anti-DoS)          */

/* IPv6 extension-header "Next Header" values that chain further. */
#define EXT_HOP_BY_HOP 0
#define EXT_ROUTING   43
#define EXT_FRAGMENT  44
#define EXT_DEST_OPTS 60
#define EXT_NO_NEXT   59

npa_result_t decode_ipv6(const u8 *buf, u32 len, u32 off,
                         decoded_packet_t *out,
                         u32 *next_off, u8 *l4_proto) {
    if (!in_bounds(len, off, IPV6_HDR_LEN)) return NPA_ERR_TRUNCATED;

    u8 version = (u8)(buf[off] >> 4);
    if (version != 6) return NPA_ERR_MALFORMED;

    out->ipv6.version       = 6;
    out->ipv6.traffic_class = (u8)(((buf[off] & 0x0Fu) << 4) | (buf[off + 1] >> 4));
    out->ipv6.flow_label    = ((u32)(buf[off + 1] & 0x0Fu) << 16) |
                              ((u32)buf[off + 2] << 8) | (u32)buf[off + 3];
    out->ipv6.payload_len   = rd_be16(buf, off + 4);
    out->ipv6.hop_limit     = buf[off + 7];
    memcpy(out->ipv6.src, &buf[off + 8], 16);
    memcpy(out->ipv6.dst, &buf[off + 24], 16);

    out->l3_span = (layer_span_t){ .offset = (u16)off,
                                   .length = (u16)IPV6_HDR_LEN,
                                   .present = true };

    /* Walk the extension-header chain to the upper-layer protocol. */
    u8  nh = buf[off + 6];
    u32 p  = off + IPV6_HDR_LEN;
    for (u32 i = 0; i < IPV6_MAX_EXT; ++i) {
        if (nh == EXT_HOP_BY_HOP || nh == EXT_ROUTING || nh == EXT_DEST_OPTS) {
            if (!in_bounds(len, p, 2)) return NPA_ERR_TRUNCATED;
            u32 ext_len = ((u32)buf[p + 1] + 1u) * 8u;  /* in 8-octet units */
            if (!in_bounds(len, p, ext_len)) return NPA_ERR_TRUNCATED;
            nh = buf[p];
            p += ext_len;
        } else if (nh == EXT_FRAGMENT) {
            if (!in_bounds(len, p, 8)) return NPA_ERR_TRUNCATED;
            nh = buf[p];
            p += 8;
        } else {
            break;  /* upper-layer protocol reached */
        }
    }

    out->ipv6.next_header = nh;
    *l4_proto = nh;
    *next_off = p;
    return NPA_OK;
}
