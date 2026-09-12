/* SPDX-License-Identifier: MIT
 *
 * util/config.c — defaults, config-file parsing, env overrides, CLI parsing.
 *
 * Precedence (later wins): defaults < /etc/npa/npa.conf < ./npa.conf <
 * $NPA_* env < CLI flags. main.c drives the order; this file implements each
 * stage.
 */
#include "util/config.h"

#include "common/packet.h"  /* NPA_MAX_FRAME_LEN */
#include "npa/npa.h"        /* NPA_VERSION_STRING */
#include "util/log.h"

#include <ctype.h>
#include <getopt.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>   /* strcasecmp */

void config_defaults(npa_config_t *cfg) {
    memset(cfg, 0, sizeof *cfg);
    cfg->interface     = NULL;
    cfg->pcap_file     = NULL;
    cfg->bpf_filter    = NULL;
    cfg->snaplen       = 65535;             /* full frames; fits NPA_MAX_FRAME_LEN */
    cfg->promiscuous   = true;

    cfg->ring_size     = 1024;              /* slots; ~64 MB at default snaplen */
    cfg->ring_policy   = RING_FULL_DROP;
    cfg->workers       = 1;

    cfg->rules_file    = NULL;

    cfg->headless      = false;
    cfg->json          = false;
    cfg->count         = 0;
    cfg->stats_interval = 0;
    cfg->write_file    = NULL;
    cfg->refresh_hz    = 30;
    cfg->log_level     = LOG_INFO;
    cfg->log_file      = NULL;               /* stderr until UI starts          */
}

/* ---- small string helpers --------------------------------------------- */

static char *trim(char *s) {
    while (*s && isspace((unsigned char)*s)) s++;
    if (*s == '\0') return s;
    char *end = s + strlen(s) - 1;
    while (end > s && isspace((unsigned char)*end)) *end-- = '\0';
    /* strip a single layer of surrounding quotes */
    size_t n = strlen(s);
    if (n >= 2 && ((s[0] == '"' && s[n - 1] == '"') ||
                   (s[0] == '\'' && s[n - 1] == '\''))) {
        s[n - 1] = '\0';
        s++;
    }
    return s;
}

static bool parse_bool(const char *v) {
    return strcmp(v, "1") == 0 || strcasecmp(v, "true") == 0 ||
           strcasecmp(v, "yes") == 0 || strcasecmp(v, "on") == 0;
}

static void store_str(char *dst, size_t cap, const char **field, const char *val) {
    if (!val || !*val) return;
    snprintf(dst, cap, "%s", val);
    *field = dst;
}

/* ---- config file ------------------------------------------------------- */

static void apply_kv(npa_config_t *cfg, const char *key, const char *val) {
    if      (!strcmp(key, "interface"))   store_str(cfg->_store_iface, sizeof cfg->_store_iface, &cfg->interface, val);
    else if (!strcmp(key, "pcap_file"))   store_str(cfg->_store_pcap, sizeof cfg->_store_pcap, &cfg->pcap_file, val);
    else if (!strcmp(key, "bpf_filter"))  store_str(cfg->_store_bpf, sizeof cfg->_store_bpf, &cfg->bpf_filter, val);
    else if (!strcmp(key, "rules_file"))  store_str(cfg->_store_rules, sizeof cfg->_store_rules, &cfg->rules_file, val);
    else if (!strcmp(key, "log_file"))    store_str(cfg->_store_log, sizeof cfg->_store_log, &cfg->log_file, val);
    else if (!strcmp(key, "write_file"))  store_str(cfg->_store_write, sizeof cfg->_store_write, &cfg->write_file, val);
    else if (!strcmp(key, "snaplen"))     cfg->snaplen = atoi(val);
    else if (!strcmp(key, "promiscuous")) cfg->promiscuous = parse_bool(val);
    else if (!strcmp(key, "ring_size"))   cfg->ring_size = (size_t)strtoul(val, NULL, 10);
    else if (!strcmp(key, "ring_full_policy")) cfg->ring_policy = !strcmp(val, "block") ? RING_FULL_BLOCK : RING_FULL_DROP;
    else if (!strcmp(key, "workers"))     cfg->workers = atoi(val);
    else if (!strcmp(key, "headless"))    cfg->headless = parse_bool(val);
    else if (!strcmp(key, "json"))        cfg->json = parse_bool(val);
    else if (!strcmp(key, "refresh_hz"))  cfg->refresh_hz = atoi(val);
    else if (!strcmp(key, "stats_interval")) cfg->stats_interval = atoi(val);
    else if (!strcmp(key, "log_level"))   cfg->log_level = log_level_from_str(val);
    else LOG_W("config: unknown key '%s' (ignored)", key);
}

npa_result_t config_load_file(npa_config_t *cfg, const char *path) {
    FILE *f = fopen(path, "r");
    if (!f) return NPA_ERR_NOTFOUND;

    char line[2048];
    int lineno = 0;
    while (fgets(line, sizeof line, f)) {
        lineno++;
        char *p = trim(line);
        if (*p == '\0' || *p == '#') continue;     /* blank / comment */

        char *eq = strchr(p, '=');
        if (!eq) {
            LOG_W("config: %s:%d: no '=' (ignored)", path, lineno);
            continue;
        }
        *eq = '\0';
        char *key = trim(p);
        char *val = trim(eq + 1);
        apply_kv(cfg, key, val);
    }
    fclose(f);
    LOG_I("config: loaded %s", path);
    return NPA_OK;
}

/* ---- environment ------------------------------------------------------- */

static const char *env_or(const char *key, const char *fallback) {
    const char *v = getenv(key);
    return (v && *v) ? v : fallback;
}

npa_result_t config_apply_env(npa_config_t *cfg) {
    cfg->interface  = env_or("NPA_INTERFACE",  cfg->interface);
    cfg->pcap_file  = env_or("NPA_PCAP_FILE",  cfg->pcap_file);
    cfg->bpf_filter = env_or("NPA_BPF_FILTER", cfg->bpf_filter);
    cfg->rules_file = env_or("NPA_RULES_FILE", cfg->rules_file);
    cfg->write_file = env_or("NPA_WRITE",      cfg->write_file);

    const char *snaplen = getenv("NPA_SNAPLEN");
    if (snaplen) cfg->snaplen = atoi(snaplen);

    const char *ring = getenv("NPA_RING_SIZE");
    if (ring) cfg->ring_size = (size_t)strtoul(ring, NULL, 10);

    const char *policy = getenv("NPA_RING_FULL_POLICY");
    if (policy) cfg->ring_policy = strcmp(policy, "block") == 0 ? RING_FULL_BLOCK : RING_FULL_DROP;

    const char *promisc = getenv("NPA_PROMISC");
    if (promisc) cfg->promiscuous = atoi(promisc) != 0;

    const char *headless = getenv("NPA_HEADLESS");
    if (headless) cfg->headless = atoi(headless) != 0;

    const char *json = getenv("NPA_JSON");
    if (json) cfg->json = atoi(json) != 0;

    const char *count = getenv("NPA_COUNT");
    if (count) cfg->count = atol(count);

    const char *si = getenv("NPA_STATS_INTERVAL");
    if (si) cfg->stats_interval = atoi(si);

    const char *hz = getenv("NPA_REFRESH_HZ");
    if (hz) cfg->refresh_hz = atoi(hz);

    cfg->log_file  = env_or("NPA_LOG_FILE", cfg->log_file);
    if (getenv("NPA_LOG_LEVEL"))
        cfg->log_level = log_level_from_str(getenv("NPA_LOG_LEVEL"));
    return NPA_OK;
}

/* ---- CLI --------------------------------------------------------------- */

void config_print_usage(const char *argv0) {
    fprintf(stderr,
        "Usage: %s [options]\n"
        "\n"
        "Source (choose one):\n"
        "  -i, --interface <if>   Capture live on interface\n"
        "  -r, --read <file>      Read packets from a .pcap file\n"
        "\n"
        "Capture:\n"
        "  -f, --filter <bpf>     BPF filter expression\n"
        "  -s, --snaplen <n>      Bytes captured per packet (default 65535)\n"
        "  -p, --no-promisc       Disable promiscuous mode\n"
        "  -w, --write <file>     Also write captured packets to a .pcap\n"
        "\n"
        "Pipeline:\n"
        "      --ring-size <n>    Ring buffer slots (default 1024)\n"
        "      --block            Block (don't drop) when ring is full\n"
        "\n"
        "Detection / UI:\n"
        "      --rules <file>     Anomaly rule file\n"
        "      --headless         Run without the ncurses UI\n"
        "      --json             Headless: emit JSON lines\n"
        "      --count <n>        Stop after N packets (0 = unlimited)\n"
        "      --stats-interval <s> Headless: print stats every S seconds\n"
        "      --log-file <path>  Log destination (default stderr)\n"
        "      --log-level <lvl>  trace|debug|info|warn|error\n"
        "  -h, --help             Show this help and exit\n"
        "  -V, --version          Print version and exit\n",
        argv0);
}

npa_result_t config_parse_args(npa_config_t *cfg, int argc, char **argv,
                               bool *should_exit, int *exit_code) {
    *should_exit = false;
    *exit_code   = 0;

    enum { OPT_RING = 256, OPT_BLOCK, OPT_RULES, OPT_HEADLESS, OPT_JSON,
           OPT_COUNT, OPT_STATS, OPT_LOGFILE, OPT_LOGLEVEL };
    static const struct option longs[] = {
        {"interface",      required_argument, 0, 'i'},
        {"read",           required_argument, 0, 'r'},
        {"filter",         required_argument, 0, 'f'},
        {"snaplen",        required_argument, 0, 's'},
        {"no-promisc",     no_argument,       0, 'p'},
        {"write",          required_argument, 0, 'w'},
        {"ring-size",      required_argument, 0, OPT_RING},
        {"block",          no_argument,       0, OPT_BLOCK},
        {"rules",          required_argument, 0, OPT_RULES},
        {"headless",       no_argument,       0, OPT_HEADLESS},
        {"json",           no_argument,       0, OPT_JSON},
        {"count",          required_argument, 0, OPT_COUNT},
        {"stats-interval", required_argument, 0, OPT_STATS},
        {"log-file",       required_argument, 0, OPT_LOGFILE},
        {"log-level",      required_argument, 0, OPT_LOGLEVEL},
        {"help",           no_argument,       0, 'h'},
        {"version",        no_argument,       0, 'V'},
        {0, 0, 0, 0},
    };

    int c;
    optind = 1;
    while ((c = getopt_long(argc, argv, "i:r:f:s:w:phV", longs, NULL)) != -1) {
        switch (c) {
            case 'i': cfg->interface   = optarg; break;
            case 'r': cfg->pcap_file   = optarg; break;
            case 'f': cfg->bpf_filter  = optarg; break;
            case 's': cfg->snaplen     = atoi(optarg); break;
            case 'p': cfg->promiscuous = false; break;
            case 'w': cfg->write_file  = optarg; break;
            case OPT_RING:  cfg->ring_size   = (size_t)strtoul(optarg, NULL, 10); break;
            case OPT_BLOCK: cfg->ring_policy = RING_FULL_BLOCK; break;
            case OPT_RULES: cfg->rules_file  = optarg; break;
            case OPT_HEADLESS: cfg->headless = true; break;
            case OPT_JSON:     cfg->json = true; cfg->headless = true; break;
            case OPT_COUNT:    cfg->count = atol(optarg); break;
            case OPT_STATS:    cfg->stats_interval = atoi(optarg); break;
            case OPT_LOGFILE:  cfg->log_file = optarg; break;
            case OPT_LOGLEVEL: cfg->log_level = log_level_from_str(optarg); break;
            case 'V':
                printf("npa %s\n", NPA_VERSION_STRING);
                *should_exit = true; *exit_code = 0; return NPA_OK;
            case 'h':
                config_print_usage(argv[0]);
                *should_exit = true; *exit_code = 0; return NPA_OK;
            default:
                config_print_usage(argv[0]);
                *should_exit = true; *exit_code = 2; return NPA_ERR_INVAL;
        }
    }
    return NPA_OK;
}

npa_result_t config_validate(const npa_config_t *cfg) {
    if (cfg->interface && cfg->pcap_file) {
        fprintf(stderr, "error: choose only one of --interface / --read\n");
        return NPA_ERR_INVAL;
    }
    if (!cfg->interface && !cfg->pcap_file) {
        fprintf(stderr, "error: a source is required (--interface or --read)\n");
        return NPA_ERR_INVAL;
    }
    if (cfg->snaplen <= 0 || cfg->snaplen > (int)NPA_MAX_FRAME_LEN) {
        fprintf(stderr, "error: --snaplen must be in 1..%u\n", NPA_MAX_FRAME_LEN);
        return NPA_ERR_INVAL;
    }
    if (cfg->ring_size < 2) {
        fprintf(stderr, "error: --ring-size must be >= 2\n");
        return NPA_ERR_INVAL;
    }
    if (cfg->count < 0) {
        fprintf(stderr, "error: --count must be >= 0\n");
        return NPA_ERR_INVAL;
    }
    return NPA_OK;
}
