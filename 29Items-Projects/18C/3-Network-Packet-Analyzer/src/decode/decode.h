/* SPDX-License-Identifier: MIT
 *
 * decode/decode.h — protocol dissection.
 *
 * Decoders are PURE: input is a byte slice, output is fields + spans written
 * into a decoded_packet_t. No allocation, no logging on the hot path, no
 * globals. Every read is bounds-checked — a hostile/truncated packet must
 * never read out of bounds (see ARCHITECTURE §2.5/§2.6).
 *
 * Each per-layer decoder takes the frame bytes, the offset where its header
 * begins, and the total caplen; it fills its view + span and reports the next
 * offset and next protocol to the caller.
 */
#ifndef NPA_DECODE_DECODE_H
#define NPA_DECODE_DECODE_H

#include "common/packet.h"
#include "common/types.h"

/*
 * Top-level entry: dissect one captured frame into `out`. Always returns
 * NPA_OK and produces a best-effort decode; per-layer failures set
 * out->partial and stop the chain rather than failing the whole call.
 */
npa_result_t decode_frame(const captured_frame_t *frame, decoded_packet_t *out);

/* ---- Per-layer decoders (also called directly from unit tests) ---------- */

/*
 * Ethernet II / 802.1Q. On success writes out->eth + out->l2_span, sets
 * *next_off to the start of the L3 header and *ethertype to the resolved type.
 */
npa_result_t decode_ethernet(const u8 *buf, u32 len, u32 off,
                             decoded_packet_t *out,
                             u32 *next_off, u16 *ethertype);

/*
 * IPv4. Writes out->ipv4 + out->l3_span, sets *next_off past the (variable)
 * IHL header and *l4_proto to the IANA protocol number. Also verifies the
 * IPv4 header checksum into out->l3_checksum_{checked,ok}.
 */
npa_result_t decode_ipv4(const u8 *buf, u32 len, u32 off,
                         decoded_packet_t *out,
                         u32 *next_off, u8 *l4_proto);

/*
 * IPv6. Parses the 40-byte base header and walks extension headers
 * (hop-by-hop, routing, fragment, dest-opts) to the final upper-layer
 * protocol. Writes out->ipv6 + out->l3_span; *l4_proto = upper-layer proto.
 */
npa_result_t decode_ipv6(const u8 *buf, u32 len, u32 off,
                         decoded_packet_t *out,
                         u32 *next_off, u8 *l4_proto);

/* TCP. Writes out->tcp + out->l4_span; *next_off = start of payload. */
npa_result_t decode_tcp(const u8 *buf, u32 len, u32 off,
                        decoded_packet_t *out, u32 *next_off);

/* UDP. Writes out->udp + out->l4_span; *next_off = start of payload. */
npa_result_t decode_udp(const u8 *buf, u32 len, u32 off,
                        decoded_packet_t *out, u32 *next_off);

/* ICMP. Writes out->icmp + out->l4_span. */
npa_result_t decode_icmp(const u8 *buf, u32 len, u32 off,
                         decoded_packet_t *out);

/* ---- Small helpers shared by decoders (inline, bounds-safe) ------------ */

/* True iff [off, off+need) lies fully within [0, len). Overflow-safe. */
static inline bool in_bounds(u32 len, u32 off, u32 need) {
    return need <= len && off <= len - need;
}

/* Read a big-endian u16/u32 at off, assuming in_bounds was already checked. */
static inline u16 rd_be16(const u8 *b, u32 off) {
    return (u16)((u16)b[off] << 8 | (u16)b[off + 1]);
}
static inline u32 rd_be32(const u8 *b, u32 off) {
    return (u32)b[off] << 24 | (u32)b[off + 1] << 16 |
           (u32)b[off + 2] << 8 | (u32)b[off + 3];
}

/* ---- Internet checksum (RFC 1071) -------------------------------------- *
 *
 * Accumulate the 16-bit one's-complement sum of `len` bytes (big-endian word
 * order, odd byte padded) into the running 32-bit accumulator `acc`.
 * Endianness-independent: it reads raw bytes.
 */
u32 npa_cksum_accumulate(const u8 *data, u32 len, u32 acc);

/* Fold a 32-bit accumulator to 16 bits and return its one's complement.
 * A buffer that INCLUDES a valid stored checksum folds to 0. */
u16 npa_cksum_fold(u32 acc);

#endif /* NPA_DECODE_DECODE_H */
