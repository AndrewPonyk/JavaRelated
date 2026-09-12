/* SPDX-License-Identifier: MIT
 *
 * util/config.h — immutable runtime configuration.
 *
 * Resolution precedence (later wins): defaults < /etc/npa/npa.conf <
 * ./npa.conf < $NPA_* env < CLI flags. Parsed ONCE at startup; passed by
 * const pointer everywhere. Live changes (e.g. new BPF) go through the
 * capture control channel, not by mutating this struct.
 *
 * String fields point either at stable env/argv memory or at the embedded
 * _store buffers (used by config_load_file), so the struct owns its strings
 * for its whole lifetime with no heap and no leak.
 */
#ifndef NPA_UTIL_CONFIG_H
#define NPA_UTIL_CONFIG_H

#include "common/types.h"
#include "util/log.h"

typedef enum {
    RING_FULL_DROP = 0,   /* drop newest + bump counter (default)            */
    RING_FULL_BLOCK,      /* block the capture thread until space frees      */
} ring_full_policy_t;

typedef struct npa_config {
    /* Source: exactly one of interface / pcap_file is used. */
    const char *interface;       /* NIC name, e.g. "eth0"; NULL if file mode */
    const char *pcap_file;       /* offline replay path; NULL if live        */
    const char *bpf_filter;      /* BPF expression, or NULL for "all"        */
    int         snaplen;         /* bytes captured per packet                */
    bool        promiscuous;     /* promiscuous mode on the NIC              */

    /* Pipeline. */
    size_t             ring_size;     /* slots; rounded up to power of two   */
    ring_full_policy_t ring_policy;
    int                workers;       /* analyzer threads (Phase 3: >1)      */

    /* Detection. */
    const char *rules_file;      /* anomaly rule file; NULL = built-ins only */

    /* Output / UI / logging. */
    bool        headless;        /* true = no ncurses (CI/scripting)         */
    bool        json;            /* headless: emit JSON lines                */
    long        count;           /* stop after N packets (0 = unlimited)     */
    int         stats_interval;  /* headless: secs between stats (0 = final) */
    const char *write_file;      /* -w: dump captured packets to this pcap   */
    int         refresh_hz;      /* UI redraw cap                            */
    log_level_t log_level;
    const char *log_file;        /* NULL = stderr                            */

    /* Embedded owned-string storage (config_load_file copies into these). */
    char _store_iface[64];
    char _store_pcap[1024];
    char _store_bpf[1024];
    char _store_rules[1024];
    char _store_log[1024];
    char _store_write[1024];
} npa_config_t;

/* Populate cfg with built-in defaults. Always succeeds. */
void config_defaults(npa_config_t *cfg);

/*
 * Merge key=value settings from `path` into cfg (between defaults and env in
 * the precedence chain). Missing file → NPA_ERR_NOTFOUND (caller may ignore).
 * Unknown keys / malformed lines are logged and skipped (best-effort).
 */
npa_result_t config_load_file(npa_config_t *cfg, const char *path);

/* Apply $NPA_* environment overrides on top of an existing cfg. */
npa_result_t config_apply_env(npa_config_t *cfg);

/*
 * Parse argv into cfg (CLI has highest precedence). Returns NPA_OK on success.
 * On `-h`/`--help`/`-V` or a parse error, prints output and sets *should_exit.
 */
npa_result_t config_parse_args(npa_config_t *cfg, int argc, char **argv,
                               bool *should_exit, int *exit_code);

/* Validate a fully-merged config (e.g. exactly one source set). */
npa_result_t config_validate(const npa_config_t *cfg);

/* Print CLI usage to stderr. */
void config_print_usage(const char *argv0);

#endif /* NPA_UTIL_CONFIG_H */
