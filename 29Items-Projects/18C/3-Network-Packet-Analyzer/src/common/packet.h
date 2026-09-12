/* SPDX-License-Identifier: MIT
 *
 * common/packet.h — the data contract shared by capture, decode, analysis, ui.
 *
 * Two key structs:
 *   captured_frame_t  — raw bytes + metadata as produced by the capture thread
 *                       and carried through the ring buffer. OWNS its bytes.
 *   decoded_packet_t  — the dissected view produced by decode/. Holds OFFSETS
 *                       into the frame (zero-copy) plus parsed header fields.
 *
 * LEAF-ish: includes only common/types.h.
 */
#ifndef NPA_COMMON_PACKET_H
#define NPA_COMMON_PACKET_H

#include "common/types.h"

/* Upper bound on bytes we retain per frame (snaplen ceiling). Tunable. */
#define NPA_MAX_FRAME_LEN 65535u

/* pcap DLT_* link types we understand (avoid depending on <pcap.h> here). */
#define NPA_DLT_NULL      0   /* BSD loopback: 4-byte AF_ family header        */
#define NPA_DLT_EN10MB    1   /* Ethernet                                      */
#define NPA_DLT_RAW     101   /* raw IP (no link header)                       */
#define NPA_DLT_LINUX_SLL 113 /* Linux "cooked" capture (any device)           */

/* ---- Layer 2/3/4 protocol identifiers (our own normalized enums) -------- */

typedef enum {
    L3_NONE = 0,
    L3_IPV4,
    L3_IPV6,
    L3_ARP,
    L3_OTHER,
} l3_proto_t;

typedef enum {
    L4_NONE = 0,
    L4_TCP,
    L4_UDP,
    L4_ICMP,
    L4_ICMPV6,
    L4_OTHER,
} l4_proto_t;

/* A parsed layer's location within the frame buffer (zero-copy slice). */
typedef struct {
    u16 offset;   /* byte offset of this header within frame->data           */
    u16 length;   /* header length in bytes                                   */
    bool present; /* was this layer successfully parsed?                      */
} layer_span_t;

/* ---- captured_frame_t: produced by capture/, flows through the ring ----- */

typedef struct {
    u64 ts_sec;                       /* capture timestamp (seconds)          */
    u64 ts_usec;                      /* capture timestamp (microseconds)     */
    u32 caplen;                       /* bytes actually captured (use THIS)   */
    u32 wirelen;                      /* original on-wire length               */
    u32 datalink;                     /* pcap DLT_* link type                 */
    u8  data[NPA_MAX_FRAME_LEN];      /* owned copy of the frame bytes        */
} captured_frame_t;

/* ---- Parsed header views (host byte order, already ntoh'd) -------------- */

typedef struct {
    u8   src[6];
    u8   dst[6];
    u16  ethertype;
    bool has_vlan;     /* at least one 802.1Q tag was present                 */
    u16  vlan_id;      /* outermost VLAN id (0..4095)                         */
    u8   vlan_count;   /* number of stacked tags unwrapped (Q-in-Q)          */
} eth_view_t;

typedef struct {
    u8   version;       /* 4                                                   */
    u8   ihl_bytes;     /* header length in bytes (IHL * 4)                    */
    u8   ttl;
    u8   protocol;      /* IANA proto number                                   */
    u16  total_len;
    u16  id;            /* identification field                               */
    u32  src;           /* IPv4 addr, host order                              */
    u32  dst;
    bool dont_fragment; /* DF flag                                            */
    bool more_fragments;/* MF flag                                            */
    u16  frag_offset;   /* fragment offset in bytes (8 * raw field)           */
} ipv4_view_t;

typedef struct {
    u8  version;        /* 6                                                   */
    u8  traffic_class;
    u32 flow_label;     /* 20-bit                                             */
    u16 payload_len;
    u8  next_header;    /* final upper-layer proto after ext-header walk      */
    u8  hop_limit;
    u8  src[16];
    u8  dst[16];
} ipv6_view_t;

typedef struct {
    u16  src_port;
    u16  dst_port;
    u32  seq;
    u32  ack;
    u16  window;
    u8   data_off_bytes; /* header length in bytes                            */
    u8   flags;          /* FIN/SYN/RST/PSH/ACK/URG/ECE/CWR bitfield          */
    /* Parsed options (best-effort; flags say which were present). */
    bool has_mss;
    u16  mss;
    bool has_window_scale;
    u8   window_scale;
    bool sack_permitted;
    bool has_timestamps;
} tcp_view_t;

typedef struct {
    u16 src_port;
    u16 dst_port;
    u16 length;
} udp_view_t;

typedef struct {
    u8   type;
    u8   code;
    bool has_id_seq;   /* true for echo request/reply (types 8/0)             */
    u16  id;
    u16  seq;
} icmp_view_t;

/* TCP flag bits for tcp_view_t.flags */
#define TCP_FIN 0x01u
#define TCP_SYN 0x02u
#define TCP_RST 0x04u
#define TCP_PSH 0x08u
#define TCP_ACK 0x10u
#define TCP_URG 0x20u
#define TCP_ECE 0x40u
#define TCP_CWR 0x80u

/* ---- decoded_packet_t: produced by decode/, consumed by analysis/ui ----- */

typedef struct {
    const captured_frame_t *frame;  /* borrowed; not owned by this struct     */

    l3_proto_t l3;
    l4_proto_t l4;

    layer_span_t l2_span;
    layer_span_t l3_span;
    layer_span_t l4_span;
    layer_span_t payload_span;      /* application bytes after L4             */

    /* Parsed views; check the matching *_span.present before reading.        */
    eth_view_t  eth;
    ipv4_view_t ipv4;
    ipv6_view_t ipv6;
    tcp_view_t  tcp;
    udp_view_t  udp;
    icmp_view_t icmp;

    /* Checksum verification. *_checked is false when the segment was
     * truncated (caplen short) and we could not verify. */
    bool l3_checksum_checked;
    bool l3_checksum_ok;
    bool l4_checksum_checked;
    bool l4_checksum_ok;

    bool partial;   /* set if any layer hit NPA_ERR_TRUNCATED/MALFORMED       */
} decoded_packet_t;

#endif /* NPA_COMMON_PACKET_H */
