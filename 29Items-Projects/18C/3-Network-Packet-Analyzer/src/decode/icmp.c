/* SPDX-License-Identifier: MIT
 *
 * decode/icmp.c — ICMP / ICMPv6 type+code, plus echo id/seq.
 *
 * type + code are the first 2 bytes; bytes 2-3 are the checksum (verified in
 * decode.c). For echo request/reply we also surface the identifier and
 * sequence number (bytes 4-7).
 */
#include "decode/decode.h"

#define ICMP_MIN_HDR 4u   /* type, code, checksum */

/* Echo types: ICMPv4 echo reply/request (0/8), ICMPv6 echo request/reply (128/129). */
static bool is_echo(u8 type) {
    return type == 0 || type == 8 || type == 128 || type == 129;
}

npa_result_t decode_icmp(const u8 *buf, u32 len, u32 off,
                         decoded_packet_t *out) {
    if (!in_bounds(len, off, ICMP_MIN_HDR)) return NPA_ERR_TRUNCATED;

    out->icmp.type = buf[off + 0];
    out->icmp.code = buf[off + 1];

    if (is_echo(out->icmp.type) && in_bounds(len, off, 8)) {
        out->icmp.id  = rd_be16(buf, off + 4);
        out->icmp.seq = rd_be16(buf, off + 6);
        out->icmp.has_id_seq = true;
    }

    out->l4_span = (layer_span_t){ .offset = (u16)off,
                                   .length = (u16)ICMP_MIN_HDR,
                                   .present = true };
    return NPA_OK;
}
