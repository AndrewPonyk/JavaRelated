/* SPDX-License-Identifier: MIT
 *
 * ui/views.h — view-model + pane renderers.
 *
 * ui_packet_t is a self-contained snapshot of a decoded packet (parsed fields
 * plus a truncated copy of the bytes for the hex view) so the UI can render a
 * full detail/hex pane without holding a pointer into transient capture memory.
 * The pure formatters are unit-testable without a TTY; the ncurses renderers
 * are thin and run only on the UI thread.
 */
#ifndef NPA_UI_VIEWS_H
#define NPA_UI_VIEWS_H

#include "analysis/anomaly.h"
#include "analysis/statistics.h"
#include "common/packet.h"
#include "common/types.h"

/* Bytes retained per packet for the hex/detail view (bounds UI memory). */
#define NPA_DETAIL_SNAP_LEN 2048

/* A retained, self-contained packet for the list + detail/hex panes. */
typedef struct {
    u64 ts_ms;        /* monotonic arrival time (ordering/age)               */
    u64 ts_sec;       /* capture wall-clock (for export)                     */
    u64 ts_usec;

    l3_proto_t l3;
    l4_proto_t l4;
    bool partial;

    eth_view_t  eth;
    ipv4_view_t ipv4;
    ipv6_view_t ipv6;
    tcp_view_t  tcp;
    udp_view_t  udp;
    icmp_view_t icmp;

    layer_span_t l2_span;
    layer_span_t l3_span;
    layer_span_t l4_span;
    layer_span_t payload_span;

    bool l3_checksum_checked, l3_checksum_ok;
    bool l4_checksum_checked, l4_checksum_ok;

    u32 caplen;       /* original captured length                            */
    u32 wirelen;      /* original on-wire length                             */
    u32 datalink;     /* DLT_* (for export)                                  */
    u32 snap_len;     /* bytes retained in `bytes` (<= NPA_DETAIL_SNAP_LEN)  */
    u8  bytes[NPA_DETAIL_SNAP_LEN];
} ui_packet_t;

/* Build a retained ui_packet_t from a freshly decoded packet (pure). */
void view_packet_capture(ui_packet_t *out, const decoded_packet_t *pkt, u64 ts_ms);

/* ---- pure formatters (no ncurses; unit-testable) ----------------------- */

/* Format IPv4 (host order) "a.b.c.d". buf >= 16. */
void view_fmt_ipv4(u32 addr, char *buf, size_t buflen);
/* Format IPv6 (16 raw bytes) into compact hex groups. buf >= 40. */
void view_fmt_ipv6(const u8 addr[16], char *buf, size_t buflen);
/* Format a human byte rate, e.g. "12.3 MB/s". buf >= 16. */
void view_fmt_rate(double bytes_per_sec, char *buf, size_t buflen);
/* Short protocol names. */
const char *view_l3_name(l3_proto_t l3);
const char *view_l4_name(l4_proto_t l4);

#if defined(NPA_WITH_NCURSES)
#include <ncurses.h>

/* Color-pair identifiers (initialized by view_init_colors). */
enum {
    CP_DEFAULT = 0,
    CP_TCP, CP_UDP, CP_ICMP, CP_OTHER,
    CP_ALERT_HIGH, CP_ALERT_MED, CP_ALERT_LOW,
    CP_HEADER, CP_PARTIAL,
};

/* Initialize color pairs (call once after start_color()). */
void view_init_colors(void);

/*
 * Render the scrolling packet list. `pkts` is the visible slice (length
 * nvisible); `sel_rel` is the highlighted row within the slice; `total` and
 * `top_logical` drive the title's "shown a-b / total" indicator.
 */
void view_render_packet_list(WINDOW *win, const ui_packet_t *pkts, size_t nvisible,
                             size_t sel_rel, size_t total, size_t top_logical,
                             bool frozen);

/* Render the L2/L3/L4 field tree + hex/ASCII dump for one packet (or NULL). */
void view_render_detail(WINDOW *win, const ui_packet_t *pkt);

/* Render the stats dashboard (counters, rates, drops, top talkers). */
void view_render_stats(WINDOW *win, const stats_view_t *stats);

/* Render the rolling alert ticker (newest last), colored by severity. */
void view_render_alerts(WINDOW *win, const alert_t *alerts, size_t count);

/* Render the bottom key-hint/status bar onto `win` (single line). */
void view_render_status(WINDOW *win, bool frozen, const char *source,
                        bool finished);
#endif /* NPA_WITH_NCURSES */

#endif /* NPA_UI_VIEWS_H */
