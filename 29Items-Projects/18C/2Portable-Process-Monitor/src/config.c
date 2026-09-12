/* config.c — runtime configuration: defaults -> INI file -> CLI flags. */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "ppmon/config.h"
#include "ppmon/log.h"

#define MODULE "config"

void ppmon_config_defaults(ppmon_config_t *cfg) {
    if (!cfg) return;
    memset(cfg, 0, sizeof(*cfg));
    cfg->interval_ms          = 1000;
    cfg->top_n                = 15;
    cfg->csv_enabled          = 0;
    cfg->cpu_alert_high       = 85.0;
    cfg->cpu_alert_low        = 70.0;
    cfg->mem_alert_high_bytes = (uint64_t)2 * 1024 * 1024 * 1024; /* 2 GiB */
    cfg->net_enabled          = 0;
    strncpy(cfg->listen_addr, "127.0.0.1", sizeof(cfg->listen_addr) - 1);
    cfg->listen_port    = 9555;
    cfg->run_once       = 0;
    cfg->want_privilege = 0;
    cfg->log_level      = 2; /* PPMON_LOG_INFO */
}

/* --- small parsing helpers --- */

static char *trim(char *s) {
    while (*s == ' ' || *s == '\t' || *s == '\r' || *s == '\n')
        ++s;
    char *end = s + strlen(s);
    while (end > s &&
           (end[-1] == ' ' || end[-1] == '\t' || end[-1] == '\r' || end[-1] == '\n')) {
        *--end = '\0';
    }
    return s;
}

static int parse_bool(const char *v, int *out) {
    if (!_stricmp(v, "true") || !_stricmp(v, "1") || !_stricmp(v, "yes") ||
        !_stricmp(v, "on")) {
        *out = 1;
        return 1;
    }
    if (!_stricmp(v, "false") || !_stricmp(v, "0") || !_stricmp(v, "no") ||
        !_stricmp(v, "off")) {
        *out = 0;
        return 1;
    }
    return 0;
}

static int parse_u32(const char *v, uint32_t *out) {
    char *end       = NULL;
    unsigned long n = strtoul(v, &end, 10);
    if (end == v || *end != '\0') return 0;
    *out = (uint32_t)n;
    return 1;
}

static int parse_u64(const char *v, uint64_t *out) {
    char *end            = NULL;
    unsigned long long n = strtoull(v, &end, 10);
    if (end == v || *end != '\0') return 0;
    *out = (uint64_t)n;
    return 1;
}

static int parse_double(const char *v, double *out) {
    char *end = NULL;
    double d  = strtod(v, &end);
    if (end == v || *end != '\0') return 0;
    *out = d;
    return 1;
}

/* Split "addr:port" (port optional) into cfg fields and enable networking. */
static int parse_listen(ppmon_config_t *cfg, const char *spec) {
    const char *colon = strrchr(spec, ':');
    char addr[64];
    if (colon) {
        size_t alen = (size_t)(colon - spec);
        if (alen >= sizeof(addr)) return 0;
        memcpy(addr, spec, alen);
        addr[alen] = '\0';
        uint32_t port;
        if (!parse_u32(colon + 1, &port) || port == 0 || port > 65535) return 0;
        cfg->listen_port = (uint16_t)port;
    } else {
        if (strlen(spec) >= sizeof(addr)) return 0;
        strcpy(addr, spec);
    }
    strncpy(cfg->listen_addr, addr, sizeof(cfg->listen_addr) - 1);
    cfg->listen_addr[sizeof(cfg->listen_addr) - 1] = '\0';
    cfg->net_enabled                               = 1;
    return 1;
}

static void apply_ini_kv(ppmon_config_t *cfg, const char *section, const char *key,
                         const char *val) {
    int b;
    uint32_t u;
    uint64_t u64;
    double d;

    if (!_stricmp(section, "polling")) {
        if (!_stricmp(key, "interval_ms") && parse_u32(val, &u))
            cfg->interval_ms = u;
        else if (!_stricmp(key, "top_n") && parse_u32(val, &u))
            cfg->top_n = u;
    } else if (!_stricmp(section, "output")) {
        if (!_stricmp(key, "csv_enabled") && parse_bool(val, &b))
            cfg->csv_enabled = b;
        else if (!_stricmp(key, "csv_path")) {
            strncpy(cfg->csv_path, val, sizeof(cfg->csv_path) - 1);
            cfg->csv_path[sizeof(cfg->csv_path) - 1] = '\0';
        }
    } else if (!_stricmp(section, "alerts")) {
        if (!_stricmp(key, "cpu_alert_high") && parse_double(val, &d))
            cfg->cpu_alert_high = d;
        else if (!_stricmp(key, "cpu_alert_low") && parse_double(val, &d))
            cfg->cpu_alert_low = d;
        else if (!_stricmp(key, "mem_alert_high_bytes") && parse_u64(val, &u64))
            cfg->mem_alert_high_bytes = u64;
    } else if (!_stricmp(section, "network")) {
        if (!_stricmp(key, "net_enabled") && parse_bool(val, &b))
            cfg->net_enabled = b;
        else if (!_stricmp(key, "listen_addr")) {
            strncpy(cfg->listen_addr, val, sizeof(cfg->listen_addr) - 1);
            cfg->listen_addr[sizeof(cfg->listen_addr) - 1] = '\0';
        } else if (!_stricmp(key, "listen_port") && parse_u32(val, &u) && u <= 65535)
            cfg->listen_port = (uint16_t)u;
    } else if (!_stricmp(section, "runtime")) {
        if (!_stricmp(key, "want_privilege") && parse_bool(val, &b))
            cfg->want_privilege = b;
        else if (!_stricmp(key, "log_level") && parse_u32(val, &u) && u <= 5)
            cfg->log_level = (int)u;
    }
}

ppmon_status_t ppmon_config_load_ini(ppmon_config_t *cfg, const char *path) {
    if (!cfg || !path) return PPMON_ERR_INVALID_ARG;
    FILE *fp = fopen(path, "r");
    if (!fp) return PPMON_OK; /* a missing INI is fine: defaults stand */

    char line[600];
    char section[64] = "";
    int lineno       = 0;
    while (fgets(line, sizeof(line), fp)) {
        ++lineno;
        char *s = trim(line);
        if (*s == '\0' || *s == ';' || *s == '#') continue;
        if (*s == '[') {
            char *close = strchr(s, ']');
            if (!close) {
                LOG_WARN(MODULE, "%s:%d: malformed section, ignored", path, lineno);
                continue;
            }
            *close = '\0';
            strncpy(section, s + 1, sizeof(section) - 1);
            section[sizeof(section) - 1] = '\0';
            continue;
        }
        char *eq = strchr(s, '=');
        if (!eq) {
            LOG_WARN(MODULE, "%s:%d: expected key=value, ignored", path, lineno);
            continue;
        }
        *eq       = '\0';
        char *key = trim(s);
        char *val = trim(eq + 1);
        apply_ini_kv(cfg, section, key, val);
    }
    fclose(fp);
    LOG_DEBUG(MODULE, "loaded INI %s", path);
    return PPMON_OK;
}

static void print_usage(void) {
    printf("Portable Process Monitor (ppmon) %s\n"
           "Usage: ppmon [options]\n\n"
           "  --interval <ms>       polling interval (50..600000, default 1000)\n"
           "  --top <n>             processes shown in the console table (default 15)\n"
           "  --csv <path>          write a CSV time-series to <path>\n"
           "  --no-csv              disable CSV output\n"
           "  --listen <addr:port>  enable the read-only metric stream (e.g. 127.0.0.1:9555)\n"
           "  --once                run a single poll then exit\n"
           "  --privilege           request SeDebugPrivilege for full visibility\n"
           "  --log-level <0..5>    0=TRACE 1=DEBUG 2=INFO 3=WARN 4=ERROR 5=OFF\n"
           "  --version             print version and exit\n"
           "  -h, --help            show this help and exit\n",
           PPMON_VERSION_STRING);
}

/* Require a value argument for an option; prints an error and returns NULL. */
static const char *value_for(const char *opt, int *i, int argc, char **argv) {
    if (*i + 1 >= argc) {
        fprintf(stderr, "ppmon: option %s requires a value\n", opt);
        return NULL;
    }
    return argv[++(*i)];
}

ppmon_status_t ppmon_config_apply_args(ppmon_config_t *cfg, int argc, char **argv) {
    if (!cfg) return PPMON_ERR_INVALID_ARG;

    for (int i = 1; i < argc; ++i) {
        const char *a = argv[i];
        const char *v;

        if (!strcmp(a, "-h") || !strcmp(a, "--help")) {
            print_usage();
            exit(0);
        } else if (!strcmp(a, "--version")) {
            printf("ppmon %s\n", PPMON_VERSION_STRING);
            exit(0);
        } else if (!strcmp(a, "--interval")) {
            if (!(v = value_for(a, &i, argc, argv)) || !parse_u32(v, &cfg->interval_ms))
                return PPMON_ERR_INVALID_ARG;
        } else if (!strcmp(a, "--top")) {
            if (!(v = value_for(a, &i, argc, argv)) || !parse_u32(v, &cfg->top_n))
                return PPMON_ERR_INVALID_ARG;
        } else if (!strcmp(a, "--csv")) {
            if (!(v = value_for(a, &i, argc, argv))) return PPMON_ERR_INVALID_ARG;
            strncpy(cfg->csv_path, v, sizeof(cfg->csv_path) - 1);
            cfg->csv_path[sizeof(cfg->csv_path) - 1] = '\0';
            cfg->csv_enabled                         = 1;
        } else if (!strcmp(a, "--no-csv")) {
            cfg->csv_enabled = 0;
        } else if (!strcmp(a, "--listen")) {
            if (!(v = value_for(a, &i, argc, argv)) || !parse_listen(cfg, v)) {
                fprintf(stderr, "ppmon: invalid --listen value\n");
                return PPMON_ERR_INVALID_ARG;
            }
        } else if (!strcmp(a, "--once")) {
            cfg->run_once = 1;
        } else if (!strcmp(a, "--privilege")) {
            cfg->want_privilege = 1;
        } else if (!strcmp(a, "--log-level")) {
            uint32_t lvl;
            if (!(v = value_for(a, &i, argc, argv)) || !parse_u32(v, &lvl) || lvl > 5)
                return PPMON_ERR_INVALID_ARG;
            cfg->log_level = (int)lvl;
        } else {
            fprintf(stderr, "ppmon: unknown option '%s' (try --help)\n", a);
            return PPMON_ERR_INVALID_ARG;
        }
    }
    return PPMON_OK;
}

ppmon_status_t ppmon_config_validate(const ppmon_config_t *cfg) {
    if (!cfg) return PPMON_ERR_INVALID_ARG;
    if (cfg->interval_ms < 50 || cfg->interval_ms > 600000) return PPMON_ERR_INVALID_ARG;
    if (cfg->cpu_alert_low >= cfg->cpu_alert_high) return PPMON_ERR_INVALID_ARG;
    if (cfg->top_n == 0) return PPMON_ERR_INVALID_ARG;
    if (cfg->csv_enabled && cfg->csv_path[0] == '\0') return PPMON_ERR_INVALID_ARG;
    return PPMON_OK;
}
