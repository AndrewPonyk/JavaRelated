/* SPDX-License-Identifier: MIT
 *
 * decode/ethernet.c — Ethernet II + stacked 802.1Q/802.1ad VLAN unwrap.
 *
 * Layout: dst[6] src[6] [TPID TCI]* ethertype[2]. We unwrap any number of
 * stacked VLAN tags (Q-in-Q) up to a bounded depth, recording the outermost
 * VLAN id, and resolve the inner EtherType for the L3 layer.
 */
#include "decode/decode.h"

#include <string.h>

#define ETH_HDR_LEN   14u
#define ETH_ADDR_LEN   6u
#define VLAN_C_TPID 0x8100u   /* 802.1Q  (customer)  */
#define VLAN_S_TPID 0x88A8u   /* 802.1ad (service)   */
#define VLAN_MAX_TAGS  4u     /* anti-DoS cap on stacked tags */

static bool is_vlan_tpid(u16 et) {
    return et == VLAN_C_TPID || et == VLAN_S_TPID;
}

npa_result_t decode_ethernet(const u8 *buf, u32 len, u32 off,
                             decoded_packet_t *out,
                             u32 *next_off, u16 *ethertype) {
    if (!in_bounds(len, off, ETH_HDR_LEN)) return NPA_ERR_TRUNCATED;

    memcpy(out->eth.dst, &buf[off], ETH_ADDR_LEN);
    memcpy(out->eth.src, &buf[off + ETH_ADDR_LEN], ETH_ADDR_LEN);

    u16 et  = rd_be16(buf, off + 12);   /* EtherType, or the first TPID */
    u32 hdr = ETH_HDR_LEN;
    u8  tags = 0;

    /* Unwrap stacked VLAN tags: each tag is [TPID(2) already read][TCI(2)]
     * followed by the next EtherType(2). */
    while (is_vlan_tpid(et) && tags < VLAN_MAX_TAGS) {
        if (!in_bounds(len, off, hdr + 4)) return NPA_ERR_TRUNCATED;
        u16 tci = rd_be16(buf, off + hdr);          /* priority/DEI/VID */
        if (tags == 0) out->eth.vlan_id = (u16)(tci & 0x0FFFu);
        tags++;
        et = rd_be16(buf, off + hdr + 2);           /* inner EtherType/TPID */
        hdr += 4;
    }

    out->eth.ethertype  = et;
    out->eth.has_vlan   = tags > 0;
    out->eth.vlan_count = tags;
    out->l2_span = (layer_span_t){ .offset = (u16)off,
                                   .length = (u16)hdr,
                                   .present = true };
    *ethertype = et;
    *next_off  = off + hdr;
    return NPA_OK;
}
