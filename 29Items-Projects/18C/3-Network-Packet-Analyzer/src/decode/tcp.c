/* SPDX-License-Identifier: MIT
 *
 * decode/tcp.c — TCP header + options dissection.
 *
 * Reads ports, seq/ack, flags, window; honors data-offset for the variable
 * header length; and parses the common options (MSS, window scale, SACK
 * permitted, timestamps). Every option read is bounds-checked against the
 * option region implied by data-offset.
 */
#include "decode/decode.h"

#define TCP_MIN_HDR 20u

#define TCPOPT_EOL  0
#define TCPOPT_NOP  1
#define TCPOPT_MSS  2
#define TCPOPT_WSCALE 3
#define TCPOPT_SACKOK 4
#define TCPOPT_TSTAMP 8

static void parse_options(const u8 *buf, u32 opt_off, u32 opt_end,
                          decoded_packet_t *out) {
    while (opt_off < opt_end) {
        u8 kind = buf[opt_off];
        if (kind == TCPOPT_EOL) break;
        if (kind == TCPOPT_NOP) { opt_off++; continue; }

        if (opt_off + 2 > opt_end) break;          /* need kind + length */
        u8 oplen = buf[opt_off + 1];
        if (oplen < 2 || opt_off + oplen > opt_end) break;  /* malformed */

        switch (kind) {
            case TCPOPT_MSS:
                if (oplen == 4) {
                    out->tcp.has_mss = true;
                    out->tcp.mss = rd_be16(buf, opt_off + 2);
                }
                break;
            case TCPOPT_WSCALE:
                if (oplen == 3) {
                    out->tcp.has_window_scale = true;
                    out->tcp.window_scale = buf[opt_off + 2];
                }
                break;
            case TCPOPT_SACKOK:
                if (oplen == 2) out->tcp.sack_permitted = true;
                break;
            case TCPOPT_TSTAMP:
                if (oplen == 10) out->tcp.has_timestamps = true;
                break;
            default:
                break;
        }
        opt_off += oplen;
    }
}

npa_result_t decode_tcp(const u8 *buf, u32 len, u32 off,
                        decoded_packet_t *out, u32 *next_off) {
    if (!in_bounds(len, off, TCP_MIN_HDR)) return NPA_ERR_TRUNCATED;

    out->tcp.src_port = rd_be16(buf, off + 0);
    out->tcp.dst_port = rd_be16(buf, off + 2);
    out->tcp.seq      = rd_be32(buf, off + 4);
    out->tcp.ack      = rd_be32(buf, off + 8);

    u8 data_off_words = (u8)(buf[off + 12] >> 4);
    u8 data_off_bytes = (u8)(data_off_words * 4u);
    if (data_off_bytes < TCP_MIN_HDR)         return NPA_ERR_MALFORMED;
    if (!in_bounds(len, off, data_off_bytes)) return NPA_ERR_TRUNCATED;

    out->tcp.data_off_bytes = data_off_bytes;
    out->tcp.flags          = buf[off + 13];      /* CWR..FIN bitfield */
    out->tcp.window         = rd_be16(buf, off + 14);

    if (data_off_bytes > TCP_MIN_HDR) {
        parse_options(buf, off + TCP_MIN_HDR, off + data_off_bytes, out);
    }

    out->l4_span = (layer_span_t){ .offset = (u16)off,
                                   .length = (u16)data_off_bytes,
                                   .present = true };
    *next_off = off + data_off_bytes;
    return NPA_OK;
}
