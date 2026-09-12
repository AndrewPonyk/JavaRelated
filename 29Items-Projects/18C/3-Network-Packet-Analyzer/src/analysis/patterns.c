/* SPDX-License-Identifier: MIT
 *
 * analysis/patterns.c — built-in rules + rule-file parser.
 *
 * File grammar (one rule per line; '#' comments, blank lines ignored):
 *   name=<id>; sev=<info|low|medium|high|critical>; match=<tok[,tok...]>;
 *   mask=0x..; value=0x..; port=<n>; ttl=<n>; sig="bytes"; msg=<text>
 * match tokens: tcp_flags, l4_port, ip_ttl_lt, byte_sig, malformed, bad_checksum
 * sig escapes: \xHH, \n \r \t \\ \"
 * Rules are upserted by name (a file rule with a built-in's name overrides it).
 */
#include "analysis/patterns.h"

#include "util/log.h"

#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

const char *severity_str(severity_t s) {
    switch (s) {
        case SEV_INFO:     return "INFO";
        case SEV_LOW:      return "LOW";
        case SEV_MEDIUM:   return "MEDIUM";
        case SEV_HIGH:     return "HIGH";
        case SEV_CRITICAL: return "CRITICAL";
        default:           return "?";
    }
}

severity_t severity_from_str(const char *s) {
    if (!s) return SEV_INFO;
    if (!strcmp(s, "info"))     return SEV_INFO;
    if (!strcmp(s, "low"))      return SEV_LOW;
    if (!strcmp(s, "medium"))   return SEV_MEDIUM;
    if (!strcmp(s, "high"))     return SEV_HIGH;
    if (!strcmp(s, "critical")) return SEV_CRITICAL;
    return SEV_INFO;
}

u32 match_token_to_flag(const char *tok) {
    if (!strcmp(tok, "tcp_flags"))    return MATCH_TCP_FLAGS;
    if (!strcmp(tok, "l4_port"))      return MATCH_L4_PORT;
    if (!strcmp(tok, "ip_ttl_lt"))    return MATCH_IP_TTL_LT;
    if (!strcmp(tok, "byte_sig"))     return MATCH_BYTE_SIG;
    if (!strcmp(tok, "malformed"))    return MATCH_MALFORMED;
    if (!strcmp(tok, "bad_checksum")) return MATCH_BAD_CHECKSUM;
    return MATCH_NONE;
}

static rule_t *push_rule(ruleset_t *rs) {
    if (rs->count >= RULE_MAX) return NULL;
    rule_t *r = &rs->rules[rs->count++];
    memset(r, 0, sizeof *r);
    r->enabled = true;
    return r;
}

static rule_t *find_rule(ruleset_t *rs, const char *name) {
    for (size_t i = 0; i < rs->count; ++i)
        if (strcmp(rs->rules[i].name, name) == 0) return &rs->rules[i];
    return NULL;
}

void patterns_load_builtin(ruleset_t *rs) {
    rs->count = 0;
    rule_t *r;

    r = push_rule(rs);
    snprintf(r->name, sizeof r->name, "tcp-null-scan");
    snprintf(r->message, sizeof r->message, "TCP NULL scan (no flags set)");
    r->severity = SEV_MEDIUM; r->match = MATCH_TCP_FLAGS;
    r->tcp_flag_mask = 0xFF; r->tcp_flag_value = 0x00;

    r = push_rule(rs);
    snprintf(r->name, sizeof r->name, "tcp-xmas-scan");
    snprintf(r->message, sizeof r->message, "TCP XMAS scan (FIN+PSH+URG)");
    r->severity = SEV_MEDIUM; r->match = MATCH_TCP_FLAGS;
    r->tcp_flag_mask = (u8)(TCP_FIN | TCP_PSH | TCP_URG);
    r->tcp_flag_value = (u8)(TCP_FIN | TCP_PSH | TCP_URG);

    r = push_rule(rs);
    snprintf(r->name, sizeof r->name, "tcp-syn-fin");
    snprintf(r->message, sizeof r->message, "Illegal TCP SYN+FIN combination");
    r->severity = SEV_HIGH; r->match = MATCH_TCP_FLAGS;
    r->tcp_flag_mask = (u8)(TCP_SYN | TCP_FIN);
    r->tcp_flag_value = (u8)(TCP_SYN | TCP_FIN);

    r = push_rule(rs);
    snprintf(r->name, sizeof r->name, "ip-low-ttl");
    snprintf(r->message, sizeof r->message, "Unusually low IPv4 TTL (<5)");
    r->severity = SEV_LOW; r->match = MATCH_IP_TTL_LT; r->ttl = 5;

    r = push_rule(rs);
    snprintf(r->name, sizeof r->name, "bad-checksum");
    snprintf(r->message, sizeof r->message, "Invalid IP/transport checksum");
    r->severity = SEV_LOW; r->match = MATCH_BAD_CHECKSUM;

    r = push_rule(rs);
    snprintf(r->name, sizeof r->name, "malformed-packet");
    snprintf(r->message, sizeof r->message, "Malformed/partial packet");
    r->severity = SEV_LOW; r->match = MATCH_MALFORMED;

    LOG_I("patterns: loaded %zu built-in rules", rs->count);
}

/* ---- file parsing helpers ---------------------------------------------- */

static char *trim(char *s) {
    while (*s && isspace((unsigned char)*s)) s++;
    if (*s == '\0') return s;
    char *end = s + strlen(s) - 1;
    while (end > s && isspace((unsigned char)*end)) *end-- = '\0';
    size_t n = strlen(s);
    if (n >= 2 && s[0] == '"' && s[n - 1] == '"') { s[n - 1] = '\0'; s++; }
    return s;
}

static int hexval(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return 0;
}

/* Parse a (de-quoted) signature string into bytes, honoring escapes. */
static u8 parse_sig(const char *s, u8 *out, u8 cap) {
    u8 n = 0;
    for (size_t i = 0; s[i] && n < cap;) {
        if (s[i] == '\\' && s[i + 1]) {
            char c = s[i + 1];
            if (c == 'x' && isxdigit((unsigned char)s[i + 2]) &&
                isxdigit((unsigned char)s[i + 3])) {
                out[n++] = (u8)((hexval(s[i + 2]) << 4) | hexval(s[i + 3]));
                i += 4;
            } else {
                switch (c) {
                    case 'n': out[n++] = '\n'; break;
                    case 'r': out[n++] = '\r'; break;
                    case 't': out[n++] = '\t'; break;
                    default:  out[n++] = (u8)c; break;
                }
                i += 2;
            }
        } else {
            out[n++] = (u8)s[i++];
        }
    }
    return n;
}

static void apply_rule_kv(rule_t *r, const char *key, char *val, bool *have_name) {
    if (!strcmp(key, "name")) {
        snprintf(r->name, sizeof r->name, "%s", val);
        *have_name = true;
    } else if (!strcmp(key, "msg")) {
        snprintf(r->message, sizeof r->message, "%s", val);
    } else if (!strcmp(key, "sev")) {
        r->severity = severity_from_str(val);
    } else if (!strcmp(key, "match")) {
        char *sv = NULL;
        for (char *tok = strtok_r(val, ",", &sv); tok; tok = strtok_r(NULL, ",", &sv)) {
            while (*tok && isspace((unsigned char)*tok)) tok++;
            r->match |= match_token_to_flag(tok);
        }
    } else if (!strcmp(key, "mask")) {
        r->tcp_flag_mask = (u8)strtoul(val, NULL, 0);
    } else if (!strcmp(key, "value")) {
        r->tcp_flag_value = (u8)strtoul(val, NULL, 0);
    } else if (!strcmp(key, "port")) {
        r->port = (u16)strtoul(val, NULL, 0);
    } else if (!strcmp(key, "ttl")) {
        r->ttl = (u8)strtoul(val, NULL, 0);
    } else if (!strcmp(key, "sig")) {
        r->sig_len = parse_sig(val, r->sig, RULE_BYTES_MAX);
    }
    /* unknown keys silently ignored — forward-compat with newer rule files */
}

npa_result_t patterns_load_file(ruleset_t *rs, const char *path) {
    if (!rs || !path) return NPA_ERR_INVAL;
    FILE *f = fopen(path, "r");
    if (!f) {
        LOG_W("patterns: cannot open rules file '%s' (using built-ins)", path);
        return NPA_ERR_IO;
    }

    char line[2048];
    int lineno = 0, added = 0;
    while (fgets(line, sizeof line, f)) {
        lineno++;
        char *p = line;
        while (*p && isspace((unsigned char)*p)) p++;
        if (*p == '\0' || *p == '#') continue;

        rule_t r;
        memset(&r, 0, sizeof r);
        r.enabled = true;
        bool have_name = false;

        char *save = NULL;
        for (char *seg = strtok_r(p, ";", &save); seg; seg = strtok_r(NULL, ";", &save)) {
            char *eq = strchr(seg, '=');
            if (!eq) continue;
            *eq = '\0';
            char *key = trim(seg);
            char *val = trim(eq + 1);
            apply_rule_kv(&r, key, val, &have_name);
        }

        if (!have_name) {
            LOG_W("patterns: %s:%d: rule missing 'name' (skipped)", path, lineno);
            continue;
        }
        if (r.match == MATCH_NONE) {
            LOG_W("patterns: %s:%d: rule '%s' has no match (skipped)",
                  path, lineno, r.name);
            continue;
        }

        rule_t *dst = find_rule(rs, r.name);   /* upsert by name */
        if (!dst) dst = push_rule(rs);
        if (!dst) { LOG_W("patterns: rule capacity reached"); break; }
        *dst = r;
        added++;
    }
    fclose(f);
    LOG_I("patterns: merged %d rule(s) from %s (total %zu)", added, path, rs->count);
    return NPA_OK;
}
