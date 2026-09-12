/* SPDX-License-Identifier: MIT
 *
 * decode/ipv4.c — IPv4 header dissection.
 *
 * Validates version==4 and IHL bounds, parses identification/fragmentation,
 * verifies the header checksum, and advances past options (via IHL) to L4.
 */
#include "decode/decode.h"

#define IPV4_MIN_HDR 20u

#define IP_FLAG_DF 0x4000u
#define IP_FLAG_MF 0x2000u
#define IP_FRAG_MASK 0x1FFFu

npa_result_t decode_ipv4(const u8 *buf, u32 len, u32 off,
                         decoded_packet_t *out,
                         u32 *next_off, u8 *l4_proto) {
    if (!in_bounds(len, off, IPV4_MIN_HDR)) return NPA_ERR_TRUNCATED;

    u8 ver_ihl   = buf[off];
    u8 version   = (u8)(ver_ihl >> 4);
    u8 ihl_words = (u8)(ver_ihl & 0x0F);
    u8 ihl_bytes = (u8)(ihl_words * 4u);

    if (version != 4)             return NPA_ERR_MALFORMED;
    if (ihl_bytes < IPV4_MIN_HDR) return NPA_ERR_MALFORMED;     /* IHL too small */
    if (!in_bounds(len, off, ihl_bytes)) return NPA_ERR_TRUNCATED;

    out->ipv4.version   = version;
    out->ipv4.ihl_bytes = ihl_bytes;
    out->ipv4.total_len = rd_be16(buf, off + 2);
    out->ipv4.id        = rd_be16(buf, off + 4);
    out->ipv4.ttl       = buf[off + 8];
    out->ipv4.protocol  = buf[off + 9];
    out->ipv4.src       = rd_be32(buf, off + 12);
    out->ipv4.dst       = rd_be32(buf, off + 16);

    u16 flags_frag = rd_be16(buf, off + 6);
    out->ipv4.dont_fragment  = (flags_frag & IP_FLAG_DF) != 0;
    out->ipv4.more_fragments = (flags_frag & IP_FLAG_MF) != 0;
    out->ipv4.frag_offset    = (u16)((flags_frag & IP_FRAG_MASK) * 8u);

    /* Header checksum: the header (incl. its stored checksum) folds to 0. */
    u32 acc = npa_cksum_accumulate(&buf[off], ihl_bytes, 0);
    out->l3_checksum_checked = true;
    out->l3_checksum_ok      = (npa_cksum_fold(acc) == 0);

    out->l3_span = (layer_span_t){ .offset = (u16)off,
                                   .length = (u16)ihl_bytes,
                                   .present = true };
    *l4_proto = out->ipv4.protocol;
    *next_off = off + ihl_bytes;
    return NPA_OK;
}
