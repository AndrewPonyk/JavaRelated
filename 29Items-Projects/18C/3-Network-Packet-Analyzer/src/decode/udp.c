/* SPDX-License-Identifier: MIT
 *
 * decode/udp.c — UDP header dissection (fixed 8-byte header).
 */
#include "decode/decode.h"

#define UDP_HDR_LEN 8u

npa_result_t decode_udp(const u8 *buf, u32 len, u32 off,
                        decoded_packet_t *out, u32 *next_off) {
    if (!in_bounds(len, off, UDP_HDR_LEN)) return NPA_ERR_TRUNCATED;

    out->udp.src_port = rd_be16(buf, off + 0);
    out->udp.dst_port = rd_be16(buf, off + 2);
    out->udp.length   = rd_be16(buf, off + 4);
    /* bytes 6-7 = checksum; verified centrally in decode.c (it needs L3 context). */

    out->l4_span = (layer_span_t){ .offset = (u16)off,
                                   .length = (u16)UDP_HDR_LEN,
                                   .present = true };
    *next_off = off + UDP_HDR_LEN;
    return NPA_OK;
}
