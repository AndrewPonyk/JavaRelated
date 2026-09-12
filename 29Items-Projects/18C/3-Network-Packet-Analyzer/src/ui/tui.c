/* SPDX-License-Identifier: MIT
 *
 * ui/tui.c — TUI model + ncurses event loop.
 *
 * Builds with or without ncurses. With NPA_WITH_NCURSES it renders the packet
 * list, detail/hex pane, stats dashboard, and alert ticker, and handles
 * scrolling, freeze, live BPF re-filter, and pcap export. Without ncurses the
 * model bridges still work and tui_run() is a no-op.
 */
#include "ui/tui.h"

#include "ui/views.h"
#include "util/log.h"

#include <pthread.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define UI_CAP      2048    /* retained packets (list + detail/hex)           */
#define ALERT_CAP    512    /* retained alerts                                */
#define MAX_VISIBLE  512    /* cap on rows copied per frame                   */

struct tui {
    statistics_t  *stats;
    capture_ctx_t *capture;     /* control channel (nullable) */
    char           source[128];

    pthread_mutex_t lock;       /* guards pkts/alerts rings + counts */
    ui_packet_t   *pkts;        /* UI_CAP ring (heap)                */
    size_t         pkt_head;
    size_t         pkt_count;
    alert_t        alerts[ALERT_CAP];
    size_t         alert_head;
    size_t         alert_count;

    atomic_bool    stop;
    atomic_bool    frozen;      /* freeze appends to the list        */

    /* view state (UI thread only) */
    size_t selected;            /* logical index (0 = oldest)        */
    size_t top;                 /* logical index of top visible row  */
    bool   follow;              /* auto-scroll to newest             */
};

static u64 now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (u64)ts.tv_sec * 1000u + (u64)(ts.tv_nsec / 1000000L);
}

npa_result_t tui_create(tui_t **out, statistics_t *stats,
                        capture_ctx_t *capture, const char *source) {
    if (!out) return NPA_ERR_INVAL;
    tui_t *t = calloc(1, sizeof *t);
    if (!t) return NPA_ERR_NOMEM;
    t->pkts = malloc(sizeof(ui_packet_t) * UI_CAP);
    if (!t->pkts) { free(t); return NPA_ERR_NOMEM; }
    t->stats   = stats;
    t->capture = capture;
    snprintf(t->source, sizeof t->source, "%s", source ? source : "?");
    pthread_mutex_init(&t->lock, NULL);
    atomic_init(&t->stop, false);
    atomic_init(&t->frozen, false);
    t->follow = true;
    *out = t;
    return NPA_OK;
}

void tui_destroy(tui_t *t) {
    if (!t) return;
    pthread_mutex_destroy(&t->lock);
    free(t->pkts);
    free(t);
}

void tui_request_stop(tui_t *t) {
    if (t) atomic_store(&t->stop, true);
}

void tui_on_packet(const decoded_packet_t *pkt, void *user) {
    tui_t *t = user;
    if (!t || atomic_load(&t->frozen)) return;   /* frozen → stop appending */

    ui_packet_t cap;
    view_packet_capture(&cap, pkt, now_ms());

    pthread_mutex_lock(&t->lock);
    t->pkts[t->pkt_head] = cap;
    t->pkt_head = (t->pkt_head + 1) % UI_CAP;
    if (t->pkt_count < UI_CAP) t->pkt_count++;
    pthread_mutex_unlock(&t->lock);
}

void tui_on_alert(const alert_t *a, void *user) {
    tui_t *t = user;
    if (!t) return;
    pthread_mutex_lock(&t->lock);
    t->alerts[t->alert_head] = *a;
    t->alert_head = (t->alert_head + 1) % ALERT_CAP;
    if (t->alert_count < ALERT_CAP) t->alert_count++;
    pthread_mutex_unlock(&t->lock);
    LOG_W("ALERT [%s] %s", a->rule, a->message);
}

#if defined(NPA_WITH_NCURSES)
#include <locale.h>
#include <ncurses.h>

enum { W_LIST = 0, W_DETAIL, W_STATS, W_ALERTS, W_STATUS, W_COUNT };

static size_t get_count(tui_t *t) {
    pthread_mutex_lock(&t->lock);
    size_t c = t->pkt_count;
    pthread_mutex_unlock(&t->lock);
    return c;
}

/* Copy up to `want` packets starting at logical `top` into dst. */
static size_t copy_visible(tui_t *t, ui_packet_t *dst, size_t top, size_t want) {
    pthread_mutex_lock(&t->lock);
    size_t count = t->pkt_count;
    size_t n = 0;
    for (size_t i = 0; i < want && top + i < count; ++i) {
        size_t L = top + i;
        size_t phys = (t->pkt_head + UI_CAP - count + L) % UI_CAP;
        dst[n++] = t->pkts[phys];
    }
    pthread_mutex_unlock(&t->lock);
    return n;
}

static size_t copy_alerts(tui_t *t, alert_t *dst, size_t cap) {
    pthread_mutex_lock(&t->lock);
    size_t n = t->alert_count < cap ? t->alert_count : cap;
    for (size_t i = 0; i < n; ++i) {
        size_t idx = (t->alert_head + ALERT_CAP - n + i) % ALERT_CAP;
        dst[i] = t->alerts[idx];
    }
    pthread_mutex_unlock(&t->lock);
    return n;
}

static void destroy_windows(WINDOW *w[W_COUNT]) {
    for (int i = 0; i < W_COUNT; ++i) {
        if (w[i]) { delwin(w[i]); w[i] = NULL; }
    }
}

static void make_windows(WINDOW *w[W_COUNT]) {
    int H, W;
    getmaxyx(stdscr, H, W);
    if (H < 8) H = 8;
    if (W < 20) W = 20;

    int status_h = 1;
    int bottom_h = H / 4; if (bottom_h < 5) bottom_h = 5; if (bottom_h > 10) bottom_h = 10;
    int detail_h = H / 4; if (detail_h < 6) detail_h = 6; if (detail_h > 12) detail_h = 12;
    int list_h   = H - status_h - bottom_h - detail_h;
    if (list_h < 3) { list_h = 3; detail_h = 6; bottom_h = H - status_h - list_h - detail_h; }
    if (bottom_h < 3) bottom_h = 3;

    int list_w = W;
    int half   = W / 2;

    w[W_LIST]   = newwin(list_h, list_w, 0, 0);
    w[W_DETAIL] = newwin(detail_h, W, list_h, 0);
    w[W_STATS]  = newwin(bottom_h, half, list_h + detail_h, 0);
    w[W_ALERTS] = newwin(bottom_h, W - half, list_h + detail_h, half);
    w[W_STATUS] = newwin(status_h, W, H - 1, 0);
}

/* Prompt for a BPF filter on the status line and apply it live. */
static void prompt_filter(tui_t *t, WINDOW *status) {
    if (!t->capture) return;
    char buf[256] = {0};
    werase(status);
    mvwprintw(status, 0, 0, "BPF filter> ");
    wrefresh(status);

    echo(); curs_set(1); nodelay(stdscr, FALSE);
    mvwgetnstr(status, 0, 11, buf, (int)sizeof buf - 1);
    noecho(); curs_set(0); nodelay(stdscr, TRUE);

    if (buf[0]) {
        npa_result_t r = capture_set_filter(t->capture, buf);
        LOG_I("tui: live filter '%s' → %s", buf, npa_result_str(r));
    }
}

/* Export retained packets to a timestamped pcap (snap-limited fidelity). */
static void do_export(tui_t *t) {
    pthread_mutex_lock(&t->lock);
    size_t count = t->pkt_count;
    ui_packet_t *copy = count ? malloc(count * sizeof(ui_packet_t)) : NULL;
    if (copy) {
        for (size_t L = 0; L < count; ++L) {
            size_t phys = (t->pkt_head + UI_CAP - count + L) % UI_CAP;
            copy[L] = t->pkts[phys];
        }
    }
    pthread_mutex_unlock(&t->lock);

    if (!copy || count == 0) { LOG_W("tui: export — nothing retained"); free(copy); return; }

    pcap_record_t *recs = malloc(count * sizeof *recs);
    if (!recs) { free(copy); return; }
    for (size_t i = 0; i < count; ++i) {
        recs[i].ts_sec  = copy[i].ts_sec;
        recs[i].ts_usec = copy[i].ts_usec;
        recs[i].caplen  = copy[i].snap_len;
        recs[i].wirelen = copy[i].wirelen;
        recs[i].bytes   = copy[i].bytes;
    }

    char path[64];
    time_t now = time(NULL);
    struct tm tmv;
    localtime_r(&now, &tmv);
    strftime(path, sizeof path, "npa-%Y%m%d-%H%M%S.pcap", &tmv);

    npa_result_t r = capture_write_pcap(path, copy[0].datalink, recs, count);
    LOG_I("tui: exported %zu packets to %s (%s)", count, path, npa_result_str(r));
    free(recs);
    free(copy);
}

static void normalize_view(tui_t *t, size_t count, size_t rows) {
    if (count == 0) { t->selected = 0; t->top = 0; return; }
    if (rows == 0) rows = 1;
    if (t->follow) t->selected = count - 1;
    if (t->selected >= count) t->selected = count - 1;
    if (t->selected < t->top) t->top = t->selected;
    else if (t->selected >= t->top + rows) t->top = t->selected - rows + 1;
    size_t maxtop = (count > rows) ? count - rows : 0;
    if (t->top > maxtop) t->top = maxtop;
}

/* Returns false if the loop should exit. */
static bool handle_key(tui_t *t, int ch, size_t count, size_t rows,
                       WINDOW *w[W_COUNT]) {
    switch (ch) {
        case 'q': case 'Q':
            atomic_store(&t->stop, true);
            return false;
        case KEY_UP:
            t->follow = false;
            if (t->selected > 0) t->selected--;
            break;
        case KEY_DOWN:
            if (count && t->selected + 1 < count) {
                t->selected++;
                if (t->selected == count - 1) t->follow = true;
            } else {
                t->follow = true;
            }
            break;
        case KEY_PPAGE:
            t->follow = false;
            t->selected = (t->selected > rows) ? t->selected - rows : 0;
            break;
        case KEY_NPAGE:
            if (count) {
                t->selected += rows;
                if (t->selected >= count) { t->selected = count - 1; t->follow = true; }
            }
            break;
        case KEY_HOME:
            t->follow = false; t->selected = 0;
            break;
        case KEY_END:
            t->follow = true;
            break;
        case 'p': case 'P':
            atomic_store(&t->frozen, !atomic_load(&t->frozen));
            break;
        case '/': case 'f': case 'F':
            prompt_filter(t, w[W_STATUS]);
            break;
        case 'w': case 'W':
            do_export(t);
            break;
        case KEY_RESIZE:
            destroy_windows(w);
            clear();
            refresh();
            make_windows(w);
            break;
        default:
            break;
    }
    return true;
}

npa_result_t tui_run(tui_t *t, int refresh_hz) {
    if (!t) return NPA_ERR_INVAL;
    if (refresh_hz <= 0) refresh_hz = 30;

    setlocale(LC_ALL, "");
    initscr();
    cbreak();
    noecho();
    keypad(stdscr, TRUE);
    curs_set(0);
    nodelay(stdscr, TRUE);
    view_init_colors();

    WINDOW *w[W_COUNT] = {0};
    make_windows(w);

    ui_packet_t *visible = malloc(sizeof(ui_packet_t) * MAX_VISIBLE);
    alert_t     *alertbuf = malloc(sizeof(alert_t) * ALERT_CAP);
    stats_view_t sv;
    if (!visible || !alertbuf) {
        free(visible); free(alertbuf);
        destroy_windows(w); endwin();
        return NPA_ERR_NOMEM;
    }

    const int frame_ms = 1000 / refresh_hz;
    LOG_I("tui: entering event loop @ %d Hz", refresh_hz);

    while (!atomic_load(&t->stop)) {
        size_t count = get_count(t);
        int list_rows = getmaxy(w[W_LIST]) - 2;
        if (list_rows < 1) list_rows = 1;

        int ch = getch();
        if (ch != ERR) {
            if (!handle_key(t, ch, count, (size_t)list_rows, w)) break;
            count = get_count(t);
            list_rows = getmaxy(w[W_LIST]) - 2;
            if (list_rows < 1) list_rows = 1;
        }

        normalize_view(t, count, (size_t)list_rows);

        size_t want = (size_t)list_rows < MAX_VISIBLE ? (size_t)list_rows : MAX_VISIBLE;
        size_t nvis = copy_visible(t, visible, t->top, want);
        size_t sel_rel = (t->selected >= t->top) ? t->selected - t->top : 0;
        if (nvis == 0) sel_rel = 0;
        else if (sel_rel >= nvis) sel_rel = nvis - 1;

        size_t nalerts = copy_alerts(t, alertbuf, ALERT_CAP);
        if (t->stats) stats_snapshot(t->stats, &sv);
        else memset(&sv, 0, sizeof sv);

        view_render_packet_list(w[W_LIST], visible, nvis, sel_rel, count, t->top,
                                atomic_load(&t->frozen));
        view_render_detail(w[W_DETAIL], nvis ? &visible[sel_rel] : NULL);
        view_render_stats(w[W_STATS], &sv);
        view_render_alerts(w[W_ALERTS], alertbuf, nalerts);
        view_render_status(w[W_STATUS], atomic_load(&t->frozen), t->source,
                           t->capture && capture_finished(t->capture));
        doupdate();

        napms(frame_ms);
    }

    free(visible);
    free(alertbuf);
    destroy_windows(w);
    endwin();
    LOG_I("tui: event loop exited");
    return NPA_OK;
}
#else  /* !NPA_WITH_NCURSES */
npa_result_t tui_run(tui_t *t, int refresh_hz) {
    (void)t; (void)refresh_hz;
    LOG_W("tui: built without ncurses; UI disabled");
    return NPA_OK;
}
#endif
