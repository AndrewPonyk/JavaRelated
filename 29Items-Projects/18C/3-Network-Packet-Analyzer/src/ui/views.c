/* SPDX-License-Identifier: MIT
 *
 * ui/views.c — view-model builders (always compiled) + ncurses renderers
 * (compiled only with NPA_WITH_NCURSES).
 */
#include "ui/views.h"

#include "analysis/patterns.h"   /* severity_str */

#include <stdio.h>
#include <string.h>

void view_packet_capture(ui_packet_t *out, const decoded_packet_t *pkt, u64 ts_ms) {
    memset(out, 0, sizeof *out);
    out->ts_ms   = ts_ms;
    out->l3      = pkt->l3;
    out->l4      = pkt->l4;
    out->partial = pkt->partial;
    out->eth     = pkt->eth;
    out->ipv4    = pkt->ipv4;
    out->ipv6    = pkt->ipv6;
    out->tcp     = pkt->tcp;
    out->udp     = pkt->udp;
    out->icmp    = pkt->icmp;
    out->l2_span = pkt->l2_span;
    out->l3_span = pkt->l3_span;
    out->l4_span = pkt->l4_span;
    out->payload_span = pkt->payload_span;
    out->l3_checksum_checked = pkt->l3_checksum_checked;
    out->l3_checksum_ok      = pkt->l3_checksum_ok;
    out->l4_checksum_checked = pkt->l4_checksum_checked;
    out->l4_checksum_ok      = pkt->l4_checksum_ok;

    if (pkt->frame) {
        out->caplen   = pkt->frame->caplen;
        out->wirelen  = pkt->frame->wirelen;
        out->datalink = pkt->frame->datalink;
        out->ts_sec   = pkt->frame->ts_sec;
        out->ts_usec  = pkt->frame->ts_usec;
        u32 n = pkt->frame->caplen;
        if (n > NPA_DETAIL_SNAP_LEN) n = NPA_DETAIL_SNAP_LEN;
        out->snap_len = n;
        memcpy(out->bytes, pkt->frame->data, n);
    }
}

void view_fmt_ipv4(u32 addr, char *buf, size_t buflen) {
    snprintf(buf, buflen, "%u.%u.%u.%u",
             (addr >> 24) & 0xFF, (addr >> 16) & 0xFF,
             (addr >> 8) & 0xFF, addr & 0xFF);
}

void view_fmt_ipv6(const u8 addr[16], char *buf, size_t buflen) {
    snprintf(buf, buflen, "%x:%x:%x:%x:%x:%x:%x:%x",
             (unsigned)((addr[0]  << 8) | addr[1]),  (unsigned)((addr[2]  << 8) | addr[3]),
             (unsigned)((addr[4]  << 8) | addr[5]),  (unsigned)((addr[6]  << 8) | addr[7]),
             (unsigned)((addr[8]  << 8) | addr[9]),  (unsigned)((addr[10] << 8) | addr[11]),
             (unsigned)((addr[12] << 8) | addr[13]), (unsigned)((addr[14] << 8) | addr[15]));
}

void view_fmt_rate(double bps, char *buf, size_t buflen) {
    const char *units[] = {"B/s", "KB/s", "MB/s", "GB/s"};
    int u = 0;
    while (bps >= 1024.0 && u < 3) { bps /= 1024.0; u++; }
    snprintf(buf, buflen, "%.1f %s", bps, units[u]);
}

const char *view_l3_name(l3_proto_t l3) {
    switch (l3) {
        case L3_IPV4: return "IPv4";
        case L3_IPV6: return "IPv6";
        case L3_ARP:  return "ARP";
        case L3_OTHER:return "L3?";
        default:      return "-";
    }
}

const char *view_l4_name(l4_proto_t l4) {
    switch (l4) {
        case L4_TCP:    return "TCP";
        case L4_UDP:    return "UDP";
        case L4_ICMP:   return "ICMP";
        case L4_ICMPV6: return "ICMPv6";
        case L4_OTHER:  return "L4?";
        default:        return "-";
    }
}

#if defined(NPA_WITH_NCURSES)

static bool g_colors = false;

void view_init_colors(void) {
    if (!has_colors()) return;
    start_color();
#if defined(NCURSES_VERSION)
    use_default_colors();
    short bg = -1;
#else
    short bg = COLOR_BLACK;
#endif
    init_pair(CP_TCP,        COLOR_GREEN,   bg);
    init_pair(CP_UDP,        COLOR_CYAN,    bg);
    init_pair(CP_ICMP,       COLOR_YELLOW,  bg);
    init_pair(CP_OTHER,      COLOR_WHITE,   bg);
    init_pair(CP_ALERT_HIGH, COLOR_RED,     bg);
    init_pair(CP_ALERT_MED,  COLOR_MAGENTA, bg);
    init_pair(CP_ALERT_LOW,  COLOR_YELLOW,  bg);
    init_pair(CP_HEADER,     COLOR_BLACK,   COLOR_CYAN);
    init_pair(CP_PARTIAL,    COLOR_RED,     bg);
    g_colors = true;
}

static void cp_on(WINDOW *w, int cp)  { if (g_colors) wattron(w, COLOR_PAIR(cp)); }
static void cp_off(WINDOW *w, int cp) { if (g_colors) wattroff(w, COLOR_PAIR(cp)); }

static int proto_color(const ui_packet_t *p) {
    if (p->partial) return CP_PARTIAL;
    switch (p->l4) {
        case L4_TCP:    return CP_TCP;
        case L4_UDP:    return CP_UDP;
        case L4_ICMP:
        case L4_ICMPV6: return CP_ICMP;
        default:        return CP_OTHER;
    }
}

static void tcp_flags_str(u8 flags, char *buf, size_t n) {
    snprintf(buf, n, "%c%c%c%c%c%c",
             (flags & TCP_URG) ? 'U' : '.', (flags & TCP_ACK) ? 'A' : '.',
             (flags & TCP_PSH) ? 'P' : '.', (flags & TCP_RST) ? 'R' : '.',
             (flags & TCP_SYN) ? 'S' : '.', (flags & TCP_FIN) ? 'F' : '.');
}

/* Format "addr:port" (or just addr) for a packet's source or dest endpoint. */
static void endpoint_str(const ui_packet_t *p, bool src, char *buf, size_t n) {
    char a[48] = "-";
    u16 port = 0;
    bool have_port = (p->l4 == L4_TCP || p->l4 == L4_UDP);

    if (p->l3 == L3_IPV4)
        view_fmt_ipv4(src ? p->ipv4.src : p->ipv4.dst, a, sizeof a);
    else if (p->l3 == L3_IPV6)
        view_fmt_ipv6(src ? p->ipv6.src : p->ipv6.dst, a, sizeof a);

    if (p->l4 == L4_TCP) port = src ? p->tcp.src_port : p->tcp.dst_port;
    else if (p->l4 == L4_UDP) port = src ? p->udp.src_port : p->udp.dst_port;

    if (have_port) snprintf(buf, n, "%s:%u", a, (unsigned)port);
    else           snprintf(buf, n, "%s", a);
}

void view_render_packet_list(WINDOW *win, const ui_packet_t *pkts, size_t nvisible,
                             size_t sel_rel, size_t total, size_t top_logical,
                             bool frozen) {
    werase(win);
    box(win, 0, 0);
    cp_on(win, CP_HEADER);
    mvwprintw(win, 0, 2, " Packets %zu-%zu / %zu %s",
              total ? top_logical + 1 : 0,
              top_logical + nvisible, total, frozen ? "[FROZEN] " : "");
    cp_off(win, CP_HEADER);

    int rows = getmaxy(win) - 2;
    int width = getmaxx(win) - 2;
    for (int i = 0; i < rows && (size_t)i < nvisible; ++i) {
        const ui_packet_t *p = &pkts[i];
        char src[64], dst[64], flags[8] = "", line[320];
        endpoint_str(p, true, src, sizeof src);
        endpoint_str(p, false, dst, sizeof dst);
        if (p->l4 == L4_TCP) {
            char f[8]; tcp_flags_str(p->tcp.flags, f, sizeof f);
            snprintf(flags, sizeof flags, " %s", f);
        }
        snprintf(line, sizeof line, "%-7zu %-23s > %-23s %-6s %5u%s%s",
                 top_logical + (size_t)i, src, dst,
                 view_l4_name(p->l4), (unsigned)p->caplen, flags,
                 p->partial ? " !" : "");

        bool is_sel = (size_t)i == sel_rel;
        int cp = proto_color(p);
        if (is_sel) wattron(win, A_REVERSE);
        cp_on(win, cp);
        mvwprintw(win, i + 1, 1, "%-*.*s", width, width, line);
        cp_off(win, cp);
        if (is_sel) wattroff(win, A_REVERSE);
    }
    wnoutrefresh(win);
}

/* Side-by-side hex + ASCII dump; returns the next free row. */
static int render_hex(WINDOW *win, int y, int maxrow, const u8 *data, u32 len) {
    for (u32 off = 0; off < len && y < maxrow; off += 16, ++y) {
        char hex[16 * 3 + 1] = {0};
        char asc[17] = {0};
        int hp = 0;
        for (u32 j = 0; j < 16; ++j) {
            if (off + j < len) {
                u8 b = data[off + j];
                hp += snprintf(hex + hp, sizeof hex - (size_t)hp, "%02x ", b);
                asc[j] = (b >= 32 && b < 127) ? (char)b : '.';
            } else {
                hp += snprintf(hex + hp, sizeof hex - (size_t)hp, "   ");
                asc[j] = ' ';
            }
        }
        mvwprintw(win, y, 1, "%04x  %-48s |%s|", off, hex, asc);
    }
    return y;
}

void view_render_detail(WINDOW *win, const ui_packet_t *p) {
    werase(win);
    box(win, 0, 0);
    cp_on(win, CP_HEADER);
    mvwprintw(win, 0, 2, " Detail ");
    cp_off(win, CP_HEADER);

    if (!p) {
        mvwprintw(win, 1, 2, "(no packet selected)");
        wnoutrefresh(win);
        return;
    }

    int y = 1;
    int maxrow = getmaxy(win) - 1;
    char a[64], b[64];

    /* Frame + Ethernet */
    mvwprintw(win, y++, 2, "Frame: caplen=%u wirelen=%u dlt=%u ts=%llu.%06llu%s",
              p->caplen, p->wirelen, p->datalink,
              (unsigned long long)p->ts_sec, (unsigned long long)p->ts_usec,
              p->partial ? "  [PARTIAL]" : "");
    if (p->l2_span.present && p->eth.ethertype) {
        if (p->eth.has_vlan)
            mvwprintw(win, y++, 2,
                      "Eth: %02x:%02x:%02x:%02x:%02x:%02x > %02x:%02x:%02x:%02x:%02x:%02x  type=0x%04x vlan=%u(x%u)",
                      p->eth.src[0], p->eth.src[1], p->eth.src[2], p->eth.src[3], p->eth.src[4], p->eth.src[5],
                      p->eth.dst[0], p->eth.dst[1], p->eth.dst[2], p->eth.dst[3], p->eth.dst[4], p->eth.dst[5],
                      p->eth.ethertype, p->eth.vlan_id, p->eth.vlan_count);
        else
            mvwprintw(win, y++, 2,
                      "Eth: %02x:%02x:%02x:%02x:%02x:%02x > %02x:%02x:%02x:%02x:%02x:%02x  type=0x%04x",
                      p->eth.src[0], p->eth.src[1], p->eth.src[2], p->eth.src[3], p->eth.src[4], p->eth.src[5],
                      p->eth.dst[0], p->eth.dst[1], p->eth.dst[2], p->eth.dst[3], p->eth.dst[4], p->eth.dst[5],
                      p->eth.ethertype);
    }

    /* L3 */
    if (p->l3 == L3_IPV4) {
        view_fmt_ipv4(p->ipv4.src, a, sizeof a);
        view_fmt_ipv4(p->ipv4.dst, b, sizeof b);
        mvwprintw(win, y++, 2,
                  "IPv4: %s > %s  ttl=%u proto=%u id=%u len=%u%s%s csum=%s",
                  a, b, p->ipv4.ttl, p->ipv4.protocol, p->ipv4.id, p->ipv4.total_len,
                  p->ipv4.dont_fragment ? " DF" : "",
                  p->ipv4.more_fragments ? " MF" : "",
                  !p->l3_checksum_checked ? "?" : p->l3_checksum_ok ? "ok" : "BAD");
    } else if (p->l3 == L3_IPV6) {
        view_fmt_ipv6(p->ipv6.src, a, sizeof a);
        view_fmt_ipv6(p->ipv6.dst, b, sizeof b);
        mvwprintw(win, y++, 2, "IPv6: %s > %s  hop=%u nh=%u plen=%u",
                  a, b, p->ipv6.hop_limit, p->ipv6.next_header, p->ipv6.payload_len);
    }

    /* L4 */
    if (p->l4 == L4_TCP) {
        char f[8]; tcp_flags_str(p->tcp.flags, f, sizeof f);
        mvwprintw(win, y++, 2,
                  "TCP: %u > %u  flags=%s seq=%u ack=%u win=%u%s%s csum=%s",
                  (unsigned)p->tcp.src_port, (unsigned)p->tcp.dst_port, f,
                  p->tcp.seq, p->tcp.ack, (unsigned)p->tcp.window,
                  p->tcp.has_mss ? " mss" : "", p->tcp.sack_permitted ? " sackOK" : "",
                  !p->l4_checksum_checked ? "?" : p->l4_checksum_ok ? "ok" : "BAD");
    } else if (p->l4 == L4_UDP) {
        mvwprintw(win, y++, 2, "UDP: %u > %u  len=%u csum=%s",
                  (unsigned)p->udp.src_port, (unsigned)p->udp.dst_port,
                  (unsigned)p->udp.length,
                  !p->l4_checksum_checked ? "?" : p->l4_checksum_ok ? "ok" : "BAD");
    } else if (p->l4 == L4_ICMP || p->l4 == L4_ICMPV6) {
        if (p->icmp.has_id_seq)
            mvwprintw(win, y++, 2, "%s: type=%u code=%u id=%u seq=%u csum=%s",
                      view_l4_name(p->l4), p->icmp.type, p->icmp.code,
                      p->icmp.id, p->icmp.seq,
                      !p->l4_checksum_checked ? "?" : p->l4_checksum_ok ? "ok" : "BAD");
        else
            mvwprintw(win, y++, 2, "%s: type=%u code=%u csum=%s",
                      view_l4_name(p->l4), p->icmp.type, p->icmp.code,
                      !p->l4_checksum_checked ? "?" : p->l4_checksum_ok ? "ok" : "BAD");
    }

    if (y < maxrow) {
        mvwhline(win, y, 1, ACS_HLINE, getmaxx(win) - 2);
        y++;
    }
    render_hex(win, y, maxrow, p->bytes, p->snap_len);
    wnoutrefresh(win);
}

void view_render_stats(WINDOW *win, const stats_view_t *s) {
    werase(win);
    box(win, 0, 0);
    cp_on(win, CP_HEADER);
    mvwprintw(win, 0, 2, " Stats ");
    cp_off(win, CP_HEADER);

    char rate[16];
    view_fmt_rate(s->bps, rate, sizeof rate);
    mvwprintw(win, 1, 2, "pkts=%llu  %.0f pps  %s",
              (unsigned long long)s->total_packets, s->pps, rate);
    mvwprintw(win, 2, 2, "TCP %llu UDP %llu ICMP %llu v6 %llu ARP %llu",
              (unsigned long long)s->tcp_packets, (unsigned long long)s->udp_packets,
              (unsigned long long)s->icmp_packets, (unsigned long long)s->ipv6_packets,
              (unsigned long long)s->arp_packets);
    mvwprintw(win, 3, 2, "alerts=%llu partial=%llu dropped=%llu ring=%llu/%llu",
              (unsigned long long)s->alerts, (unsigned long long)s->partial_packets,
              (unsigned long long)s->dropped, (unsigned long long)s->ring_used,
              (unsigned long long)s->ring_capacity);

    int y = 4, maxrow = getmaxy(win) - 1;
    if (y < maxrow) mvwprintw(win, y++, 2, "Top talkers:");
    for (int i = 0; i < STATS_TOP_TALKERS && y < maxrow; ++i) {
        if (s->talkers[i].packets == 0) break;
        char ip[16];
        view_fmt_ipv4(s->talkers[i].addr, ip, sizeof ip);
        mvwprintw(win, y++, 4, "%-15s  %llu pkts  %llu B", ip,
                  (unsigned long long)s->talkers[i].packets,
                  (unsigned long long)s->talkers[i].bytes);
    }
    wnoutrefresh(win);
}

void view_render_alerts(WINDOW *win, const alert_t *alerts, size_t count) {
    werase(win);
    box(win, 0, 0);
    cp_on(win, CP_HEADER);
    mvwprintw(win, 0, 2, " Alerts (%zu) ", count);
    cp_off(win, CP_HEADER);

    int rows = getmaxy(win) - 2;
    /* Show the newest `rows` alerts. */
    size_t start = (count > (size_t)rows) ? count - (size_t)rows : 0;
    int y = 1;
    for (size_t i = start; i < count; ++i, ++y) {
        const alert_t *a = &alerts[i];
        char src[16];
        view_fmt_ipv4(a->src_ip, src, sizeof src);
        int cp = a->severity >= SEV_HIGH ? CP_ALERT_HIGH
               : a->severity == SEV_MEDIUM ? CP_ALERT_MED : CP_ALERT_LOW;
        cp_on(win, cp);
        mvwprintw(win, y, 1, "[%-8s] %-16s %s", severity_str(a->severity), a->rule, src);
        cp_off(win, cp);
    }
    wnoutrefresh(win);
}

void view_render_status(WINDOW *win, bool frozen, const char *source, bool finished) {
    werase(win);
    cp_on(win, CP_HEADER);
    mvwprintw(win, 0, 0,
              " npa | src:%s%s | %s | UP/DN PgUp/PgDn Home/End  p:%s  /:filter  w:write  q:quit ",
              source ? source : "?",
              finished ? " (EOF)" : "",
              frozen ? "FROZEN" : "LIVE",
              frozen ? "resume" : "freeze");
    cp_off(win, CP_HEADER);
    wnoutrefresh(win);
}
#endif /* NPA_WITH_NCURSES */
