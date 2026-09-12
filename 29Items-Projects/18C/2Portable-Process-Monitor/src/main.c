/*
 * main.c — entry point and the poll-collect-store-alert-render-export loop.
 *
 * Lifecycle:
 *   1. Parse config (defaults -> INI -> CLI).
 *   2. Acquire collection (timer, enum, PDH) and presentation (UI, CSV, net).
 *   3. Loop on the drift-free timer until interrupted (Ctrl+C) or --once.
 *   4. Flush CSV, close handles/sockets, exit with a meaningful code.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#endif

#include "ppmon/ppmon.h"
#include "ppmon/config.h"
#include "ppmon/log.h"
#include "ppmon/timer.h"
#include "ppmon/process_enum.h"
#include "ppmon/metrics.h"
#include "ppmon/pdh_counters.h"
#include "ppmon/sample_store.h"
#include "ppmon/alerting.h"
#include "ppmon/csv_export.h"
#include "ppmon/console_ui.h"
#include "ppmon/net_server.h"

#define MODULE "main"
#define STORE_CAPACITY 4096
#define EVENTS_CAP 512

static volatile sig_atomic_t g_running = 1;

static void handle_sigint(int sig) {
    (void)sig;
    g_running = 0;
}

#if defined(_WIN32)
static BOOL WINAPI ctrl_handler(DWORD type) {
    (void)type;
    g_running = 0;
    return TRUE; /* handled: request graceful shutdown for all console events */
}
#endif

/* ISO-8601 UTC timestamp with millisecond precision (e.g. 2026-06-17T12:00:00.123Z). */
static void iso8601_utc(char *buf, size_t cap) {
#if defined(_WIN32)
    SYSTEMTIME st;
    GetSystemTime(&st);
    snprintf(buf, cap, "%04u-%02u-%02uT%02u:%02u:%02u.%03uZ", (unsigned)st.wYear,
             (unsigned)st.wMonth, (unsigned)st.wDay, (unsigned)st.wHour, (unsigned)st.wMinute,
             (unsigned)st.wSecond, (unsigned)st.wMilliseconds);
#else
    snprintf(buf, cap, "1970-01-01T00:00:00.000Z");
#endif
}

static unsigned cpu_count(void) {
#if defined(_WIN32)
    SYSTEM_INFO si;
    GetSystemInfo(&si);
    return si.dwNumberOfProcessors ? si.dwNumberOfProcessors : 1;
#else
    return 1;
#endif
}

/* qsort comparator: CPU percent, descending. */
static int cmp_cpu_desc(const void *a, const void *b) {
    const ppmon_proc_metrics_t *x = (const ppmon_proc_metrics_t *)a;
    const ppmon_proc_metrics_t *y = (const ppmon_proc_metrics_t *)b;
    if (x->cpu_percent < y->cpu_percent) return 1;
    if (x->cpu_percent > y->cpu_percent) return -1;
    return 0;
}

typedef struct app {
    const ppmon_config_t *cfg;
    ppmon_timer_t *timer;
    ppmon_enum_t *en;
    ppmon_pdh_t *pdh;
    ppmon_store_t *store;
    ppmon_ui_t *ui;
    ppmon_csv_t *csv;
    ppmon_net_t *net;
    ppmon_alert_engine_t *alerts;
    unsigned cores;
    ppmon_proc_metrics_t *rows;
    size_t rows_cap;
    ppmon_alert_event_t *events;
    size_t events_cap;
} app_t;

static void report_alerts(const ppmon_alert_event_t *events, size_t n) {
    for (size_t i = 0; i < n; ++i) {
        const ppmon_alert_event_t *e = &events[i];
        const char *metric           = ppmon_alert_metric_name(e->metric);
        if (e->raised) {
            LOG_WARN(MODULE, "ALERT  pid=%-6u %-24s %s=%.1f", e->pid, e->image_name, metric,
                     e->value);
        } else {
            LOG_INFO(MODULE, "clear  pid=%-6u %-24s %s", e->pid, e->image_name, metric);
        }
    }
}

/* One poll cycle: enumerate -> sample -> commit -> derive -> sort -> alert ->
 * render -> export/broadcast. */
static ppmon_status_t run_poll_cycle(app_t *app) {
    ppmon_status_t st = ppmon_enum_refresh(app->en);
    if (st != PPMON_OK) return st;

    ppmon_store_begin_cycle(app->store);

    size_t total   = ppmon_enum_count(app->en);
    size_t sampled = 0, skipped = 0;
    for (size_t i = 0; i < total; ++i) {
        ppmon_proc_id_t id;
        if (ppmon_enum_at(app->en, i, &id) != PPMON_OK) continue;

        ppmon_sample_t s;
        ppmon_status_t rc = ppmon_metrics_sample(id.pid, &s);
        if (rc != PPMON_OK) {
            ++skipped; /* protected/exited process — degrade gracefully */
            continue;
        }
        /* Identity is authoritative from the enumeration pass (consistent keying
         * and a reliable name even when the image path query came back empty). */
        s.pid              = id.pid;
        s.start_time_100ns = id.start_time_100ns;
        if (s.image_name[0] == '\0')
            strncpy(s.image_name, id.image_name, sizeof(s.image_name) - 1);

        ppmon_store_commit(app->store, &s);
        ++sampled;
    }
    LOG_TRACE(MODULE, "sampled=%zu skipped=%zu of %zu", sampled, skipped, total);

    ppmon_system_metrics_t sys;
    ppmon_pdh_collect(app->pdh, &sys); /* non-fatal: zeros on failure */

    size_t nrows = 0;
    st = ppmon_store_derive(app->store, app->timer->qpc_frequency, app->cores, app->rows,
                            app->rows_cap, &nrows);
    if (st != PPMON_OK) return st;

    qsort(app->rows, nrows, sizeof(app->rows[0]), cmp_cpu_desc);

    size_t nevents = 0;
    ppmon_alert_engine_update(app->alerts, app->rows, nrows, app->events, app->events_cap,
                              &nevents);
    report_alerts(app->events, nevents);

    ppmon_ui_render(app->ui, &sys, app->rows, nrows);

    if (app->csv) {
        char ts[32];
        iso8601_utc(ts, sizeof(ts));
        for (size_t i = 0; i < nrows; ++i)
            ppmon_csv_write_row(app->csv, ts, &app->rows[i]);
        ppmon_csv_flush(app->csv);
    }
    if (app->net) ppmon_net_broadcast(app->net, app->rows, nrows);

    return PPMON_OK;
}

int main(int argc, char **argv) {
    ppmon_config_t cfg;
    ppmon_config_defaults(&cfg);
    (void)ppmon_config_load_ini(&cfg, "ppmon.ini"); /* best-effort */

    if (ppmon_config_apply_args(&cfg, argc, argv) != PPMON_OK ||
        ppmon_config_validate(&cfg) != PPMON_OK) {
        fprintf(stderr, "ppmon %s: invalid configuration (try --help)\n",
                PPMON_VERSION_STRING);
        return 2;
    }

    ppmon_log_set_level((ppmon_log_level_t)cfg.log_level);
    signal(SIGINT, handle_sigint);
#if defined(_WIN32)
    SetConsoleCtrlHandler(ctrl_handler, TRUE);
#endif

    LOG_INFO(MODULE, "Portable Process Monitor %s starting (interval=%ums, top=%u)",
             PPMON_VERSION_STRING, cfg.interval_ms, cfg.top_n);

    if (cfg.want_privilege) {
        ppmon_status_t pst = ppmon_enable_debug_privilege();
        if (pst == PPMON_OK)
            LOG_INFO(MODULE, "SeDebugPrivilege enabled (full process visibility)");
        else
            LOG_WARN(MODULE, "could not enable SeDebugPrivilege (%s); reduced visibility",
                     ppmon_status_str(pst));
    }

    /* --- Resource acquisition (single cleanup unwind below) --- */
    ppmon_status_t st = PPMON_OK;
    ppmon_timer_t timer;
    app_t app;
    memset(&app, 0, sizeof(app));
    app.cfg        = &cfg;
    app.timer      = &timer;
    app.cores      = cpu_count();
    app.rows_cap   = STORE_CAPACITY;
    app.events_cap = EVENTS_CAP;
    int rc         = 0;

    app.rows   = malloc(app.rows_cap * sizeof(*app.rows));
    app.events = malloc(app.events_cap * sizeof(*app.events));
    if (!app.rows || !app.events) {
        st = PPMON_ERR_NO_MEMORY;
        goto cleanup;
    }

    ppmon_alert_rule_t rules[2];
    rules[0].metric = PPMON_METRIC_CPU_PERCENT;
    rules[0].high   = cfg.cpu_alert_high;
    rules[0].low    = cfg.cpu_alert_low;
    rules[0].active = 0;
    rules[1].metric = PPMON_METRIC_WORKING_SET_BYTES;
    rules[1].high   = (double)cfg.mem_alert_high_bytes;
    rules[1].low    = (double)cfg.mem_alert_high_bytes * 0.9;
    rules[1].active = 0;

    if ((st = ppmon_timer_init(&timer, cfg.interval_ms)) != PPMON_OK) goto cleanup;
    if ((st = ppmon_enum_create(&app.en)) != PPMON_OK) goto cleanup;
    if ((st = ppmon_pdh_open(&app.pdh)) != PPMON_OK) goto cleanup;
    if ((st = ppmon_store_create(STORE_CAPACITY, 2, &app.store)) != PPMON_OK) goto cleanup;
    if ((st = ppmon_ui_init(&app.ui, cfg.top_n)) != PPMON_OK) goto cleanup;
    if ((st = ppmon_alert_engine_create(rules, 2, STORE_CAPACITY, &app.alerts)) != PPMON_OK)
        goto cleanup;
    if (cfg.csv_enabled && (st = ppmon_csv_open(cfg.csv_path, &app.csv)) != PPMON_OK)
        goto cleanup;
    if (cfg.net_enabled &&
        (st = ppmon_net_start(cfg.listen_addr, cfg.listen_port, &app.net)) != PPMON_OK)
        goto cleanup;

    /* --- Main loop --- */
    do {
        st = run_poll_cycle(&app);
        if (st != PPMON_OK) {
            LOG_ERROR(MODULE, "poll cycle failed: %s", ppmon_status_str(st));
            rc = 1;
            break;
        }
        if (cfg.run_once) break;
        ppmon_timer_wait_next(&timer);
    } while (g_running);

    LOG_INFO(MODULE, "shutting down");

cleanup:
    if (st != PPMON_OK && rc == 0) {
        LOG_ERROR(MODULE, "startup failed: %s", ppmon_status_str(st));
        rc = 1;
    }
    if (app.csv) {
        ppmon_csv_flush(app.csv);
        ppmon_csv_close(app.csv);
    }
    if (app.net) ppmon_net_stop(app.net);
    if (app.alerts) ppmon_alert_engine_destroy(app.alerts);
    if (app.ui) ppmon_ui_destroy(app.ui);
    if (app.store) ppmon_store_destroy(app.store);
    if (app.pdh) ppmon_pdh_close(app.pdh);
    if (app.en) ppmon_enum_destroy(app.en);
    free(app.rows);
    free(app.events);
    return rc;
}
