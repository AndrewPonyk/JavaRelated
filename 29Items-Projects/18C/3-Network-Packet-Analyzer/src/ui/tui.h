/* SPDX-License-Identifier: MIT
 *
 * ui/tui.h — ncurses TUI lifecycle and the bridge from the analyzer thread.
 *
 * Threading rule (see TECH-NOTES): ONLY the UI thread calls ncurses. The
 * analyzer thread feeds the UI exclusively through tui_on_packet/tui_on_alert,
 * which append into mutex-guarded bounded rings; tui_run() renders from a brief
 * locked copy. Control actions (filter, export) flow UI → capture via the
 * capture handle passed at creation.
 */
#ifndef NPA_UI_TUI_H
#define NPA_UI_TUI_H

#include "analysis/analyzer.h"     /* packet_sink_fn */
#include "analysis/anomaly.h"      /* alert_t */
#include "analysis/statistics.h"
#include "capture/capture.h"       /* capture_ctx_t control channel */
#include "common/types.h"

typedef struct tui tui_t;

/*
 * Create the TUI model. `stats` is snapshotted for the dashboard; `capture`
 * (nullable) is the control channel for live filter / export; `source` labels
 * the status bar. Does NOT init ncurses yet.
 */
npa_result_t tui_create(tui_t **out, statistics_t *stats,
                        capture_ctx_t *capture, const char *source);

/*
 * Run the UI: initscr → event loop (render at refresh_hz, handle keys) →
 * endwin. Blocks until the user quits or tui_request_stop() is called.
 */
npa_result_t tui_run(tui_t *t, int refresh_hz);

/* Ask the UI loop to exit (safe from a signal handler / other thread). */
void tui_request_stop(tui_t *t);

/* Free the TUI. Call after tui_run() returns. */
void tui_destroy(tui_t *t);

/* ---- Bridges wired into the analyzer/anomaly (run on analyzer thread) --- */

/* packet_sink_fn: retain a packet for the list/detail panes. user = tui_t*. */
void tui_on_packet(const decoded_packet_t *pkt, void *user);

/* alert_sink_fn: append an alert. user = tui_t*. */
void tui_on_alert(const alert_t *a, void *user);

#endif /* NPA_UI_TUI_H */
