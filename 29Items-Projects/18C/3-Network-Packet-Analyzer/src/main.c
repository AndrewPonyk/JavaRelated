/* SPDX-License-Identifier: MIT
 *
 * main.c — entry point and orchestration.
 *
 *   1. resolve config (defaults < files < env < CLI) and validate
 *   2. init logging + signal handlers
 *   3. build the pipeline: ring → capture (producer) → analyzer (consumer),
 *      with stats + anomaly engine, and either the TUI or a headless reporter
 *   4. run until quit / signal / EOF / --count, then tear down in order
 *   5. headless: emit a final summary (JSON or human)
 */
#include "npa/npa.h"

#include "analysis/analyzer.h"
#include "analysis/anomaly.h"
#include "analysis/patterns.h"
#include "analysis/statistics.h"
#include "buffer/ring_buffer.h"
#include "capture/capture.h"
#include "ui/tui.h"
#include "ui/views.h"          /* view_fmt_ipv4 */
#include "util/config.h"
#include "util/log.h"

#include <pthread.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>

#if defined(HAVE_LIBPCAP) && !defined(NPA_HAVE_PCAP)
#  define NPA_HAVE_PCAP 1
#endif
#if defined(NPA_HAVE_PCAP)
#  include <pcap.h>
#endif

const char *npa_version(void) {
#if defined(NPA_HAVE_PCAP)
    static char buf[96];
    snprintf(buf, sizeof buf, "npa %s (%s)", NPA_VERSION_STRING, pcap_lib_version());
    return buf;
#else
    return "npa " NPA_VERSION_STRING " (built-in pcap reader; no live capture)";
#endif
}

/* --- Signal handling: only set flags + nudge the UI to stop. --- */
static tui_t *g_tui = NULL;
static volatile sig_atomic_t g_signaled = 0;

static void on_signal(int sig) {
    (void)sig;
    g_signaled = 1;
    if (g_tui) tui_request_stop(g_tui);
}

static void install_signal_handlers(void) {
#if defined(_WIN32)
    signal(SIGINT,  on_signal);
    signal(SIGTERM, on_signal);
#else
    struct sigaction sa = {0};
    sa.sa_handler = on_signal;
    sigemptyset(&sa.sa_mask);
    sigaction(SIGINT,  &sa, NULL);
    sigaction(SIGTERM, &sa, NULL);
    signal(SIGPIPE, SIG_IGN);
#endif
}

static u64 mono_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (u64)ts.tv_sec * 1000u + (u64)(ts.tv_nsec / 1000000L);
}

/* --- Headless reporter: alert sink + stats lines to stdout. --- */
typedef struct {
    bool  json;
    FILE *out;
} reporter_t;

static void reporter_on_alert(const alert_t *a, void *user) {
    reporter_t *r = user;
    char src[16], dst[16];
    view_fmt_ipv4(a->src_ip, src, sizeof src);
    view_fmt_ipv4(a->dst_ip, dst, sizeof dst);
    if (r->json) {
        fprintf(r->out,
                "{\"type\":\"alert\",\"ts_ms\":%llu,\"severity\":\"%s\","
                "\"rule\":\"%s\",\"src\":\"%s\",\"dst\":\"%s\",\"sport\":%u,"
                "\"dport\":%u,\"msg\":\"%s\"}\n",
                (unsigned long long)a->ts_ms, severity_str(a->severity), a->rule,
                src, dst, (unsigned)a->src_port, (unsigned)a->dst_port, a->message);
    } else {
        fprintf(r->out, "[ALERT] %-8s %-16s %s:%u -> %s:%u  %s\n",
                severity_str(a->severity), a->rule, src, (unsigned)a->src_port,
                dst, (unsigned)a->dst_port, a->message);
    }
    fflush(r->out);
}

static void reporter_emit_stats(reporter_t *r, const stats_view_t *s,
                                const char *type) {
    if (r->json) {
        fprintf(r->out,
                "{\"type\":\"%s\",\"packets\":%llu,\"ipv4\":%llu,\"ipv6\":%llu,"
                "\"arp\":%llu,\"tcp\":%llu,\"udp\":%llu,\"icmp\":%llu,"
                "\"partial\":%llu,\"alerts\":%llu,\"dropped\":%llu}\n",
                type, (unsigned long long)s->total_packets,
                (unsigned long long)s->ipv4_packets, (unsigned long long)s->ipv6_packets,
                (unsigned long long)s->arp_packets, (unsigned long long)s->tcp_packets,
                (unsigned long long)s->udp_packets, (unsigned long long)s->icmp_packets,
                (unsigned long long)s->partial_packets, (unsigned long long)s->alerts,
                (unsigned long long)s->dropped);
    } else {
        fprintf(r->out,
                "== %s == packets=%llu ipv4=%llu ipv6=%llu arp=%llu "
                "tcp=%llu udp=%llu icmp=%llu partial=%llu alerts=%llu dropped=%llu\n",
                type, (unsigned long long)s->total_packets,
                (unsigned long long)s->ipv4_packets, (unsigned long long)s->ipv6_packets,
                (unsigned long long)s->arp_packets, (unsigned long long)s->tcp_packets,
                (unsigned long long)s->udp_packets, (unsigned long long)s->icmp_packets,
                (unsigned long long)s->partial_packets, (unsigned long long)s->alerts,
                (unsigned long long)s->dropped);
    }
    fflush(r->out);
}

int npa_run(const npa_config_t *cfg) {
    int exit_code = 0;
    const char *source = cfg->pcap_file ? cfg->pcap_file : cfg->interface;

    ring_buffer_t *ring = NULL;
    if (ring_create(&ring, cfg->ring_size) != NPA_OK) {
        LOG_F("failed to create ring buffer");
        return 1;
    }

    statistics_t stats;
    stats_init(&stats);

    ruleset_t rules;
    patterns_load_builtin(&rules);
    if (cfg->rules_file) patterns_load_file(&rules, cfg->rules_file);

    /* Producer first, so the UI can hold the capture control handle. */
    capture_ctx_t *capture = NULL;
    anomaly_engine_t *anomaly = NULL;
    analyzer_t *analyzer = NULL;
    tui_t *tui = NULL;
    reporter_t reporter = { .json = cfg->json, .out = stdout };

    if (capture_create(&capture, cfg, ring) != NPA_OK) {
        LOG_F("failed to create capture");
        ring_destroy(ring);
        return 1;
    }

    if (!cfg->headless) {
        if (tui_create(&tui, &stats, capture, source) != NPA_OK) {
            LOG_F("failed to create TUI");
            exit_code = 1;
            goto cleanup;
        }
        g_tui = tui;
    }

    /* Always supply an alert sink: TUI in interactive mode, reporter headless. */
    if (anomaly_create(&anomaly, &rules,
                       tui ? tui_on_alert : reporter_on_alert,
                       tui ? (void *)tui : (void *)&reporter) != NPA_OK) {
        LOG_F("failed to create anomaly engine");
        exit_code = 1;
        goto cleanup;
    }

    analyzer_cfg_t acfg = {
        .ring      = ring,
        .stats     = &stats,
        .anomaly   = anomaly,
        .on_packet = tui ? tui_on_packet : NULL,
        .user      = tui,
        .count     = cfg->count,
    };
    if (analyzer_create(&analyzer, &acfg) != NPA_OK) {
        LOG_F("failed to create analyzer");
        exit_code = 1;
        goto cleanup;
    }

    analyzer_start(analyzer);
    capture_start(capture);

    if (tui) {
        tui_run(tui, cfg->refresh_hz);
    } else {
        LOG_I("headless: running until EOF / count / signal");
        u64 last = mono_ms();
        while (!g_signaled && !capture_finished(capture) && !analyzer_done(analyzer)) {
            struct timespec ts = { .tv_sec = 0, .tv_nsec = 100000000L };
            nanosleep(&ts, NULL);
            if (cfg->stats_interval > 0) {
                u64 now = mono_ms();
                if (now - last >= (u64)cfg->stats_interval * 1000u) {
                    stats_view_t sv;
                    stats_snapshot(&stats, &sv);
                    reporter_emit_stats(&reporter, &sv, "stats");
                    last = now;
                }
            }
        }
    }

    /* Orderly teardown: stop producer, then drain + stop consumer. */
    capture_stop(capture);
    analyzer_stop(analyzer);

    if (cfg->headless) {
        stats_view_t sv;
        stats_snapshot(&stats, &sv);
        reporter_emit_stats(&reporter, &sv, "summary");
    }
    LOG_I("shutdown: %llu packets analyzed",
          (unsigned long long)stats.v.total_packets);

cleanup:
    if (analyzer) analyzer_destroy(analyzer);
    if (anomaly)  anomaly_destroy(anomaly);
    if (tui)      { g_tui = NULL; tui_destroy(tui); }
    if (capture)  capture_destroy(capture);
    ring_destroy(ring);
    stats_destroy(&stats);
    return exit_code;
}

int main(int argc, char **argv) {
    npa_config_t cfg;
    config_defaults(&cfg);
    config_load_file(&cfg, "/etc/npa/npa.conf");   /* ignored if absent */
    config_load_file(&cfg, "npa.conf");            /* ignored if absent */
    config_apply_env(&cfg);

    bool should_exit = false;
    int  exit_code = 0;
    if (config_parse_args(&cfg, argc, argv, &should_exit, &exit_code) != NPA_OK
        && !should_exit) {
        return 2;
    }
    if (should_exit) return exit_code;

    if (config_validate(&cfg) != NPA_OK) return 2;

#if !defined(NPA_WITH_NCURSES)
    cfg.headless = true;   /* no TUI compiled in (e.g. Windows build) → headless */
#endif

    log_init(cfg.log_file, cfg.log_level);
    LOG_I("starting %s", npa_version());

    install_signal_handlers();

    int rc = npa_run(&cfg);

    log_shutdown();
    return rc;
}
