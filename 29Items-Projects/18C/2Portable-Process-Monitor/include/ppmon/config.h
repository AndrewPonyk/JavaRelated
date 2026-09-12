/*
 * config.h — Runtime configuration with defaults → INI → CLI precedence.
 */
#ifndef PPMON_CONFIG_H
#define PPMON_CONFIG_H

#include "ppmon/ppmon.h"

#define PPMON_PATH_MAX 512

typedef struct ppmon_config {
    uint32_t interval_ms; /* polling interval (default 1000)             */
    uint32_t top_n;       /* processes shown in the console table        */
    int csv_enabled;      /* write CSV output?                           */
    char csv_path[PPMON_PATH_MAX];

    double cpu_alert_high; /* threshold-based alerting watermarks         */
    double cpu_alert_low;
    uint64_t mem_alert_high_bytes;

    int net_enabled;      /* enable the WinSock2 metric stream?          */
    char listen_addr[64]; /* default 127.0.0.1                           */
    uint16_t listen_port; /* default 9555                                */

    int run_once;       /* single poll then exit (used by smoke test)  */
    int want_privilege; /* request SeDebugPrivilege?                   */
    int log_level;      /* see log.h                                   */
} ppmon_config_t;

/* Populate `cfg` with built-in defaults. */
void ppmon_config_defaults(ppmon_config_t *cfg);

/*
 * Layer an INI file on top of `cfg` (missing keys left untouched).
 * TODO: minimal [section] key=value parser, bounded copies, range validation.
 */
ppmon_status_t ppmon_config_load_ini(ppmon_config_t *cfg, const char *path);

/*
 * Apply CLI overrides (highest precedence). Recognises --interval, --top,
 * --csv, --listen, --once, --privilege, --log-level, etc.
 */
ppmon_status_t ppmon_config_apply_args(ppmon_config_t *cfg, int argc, char **argv);

/* Validate cross-field invariants (e.g. low < high). */
ppmon_status_t ppmon_config_validate(const ppmon_config_t *cfg);

#endif /* PPMON_CONFIG_H */
