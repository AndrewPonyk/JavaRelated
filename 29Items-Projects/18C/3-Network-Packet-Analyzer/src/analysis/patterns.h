/* SPDX-License-Identifier: MIT
 *
 * analysis/patterns.h — anomaly rule definitions + loader.
 *
 * A rule is a small declarative matcher over a decoded packet. The engine
 * (anomaly.c) walks the active rule set per packet. Rules come from two
 * sources: a compiled-in built-in set, and an optional rule file
 * (config/patterns.rules) loaded at startup.
 */
#ifndef NPA_ANALYSIS_PATTERNS_H
#define NPA_ANALYSIS_PATTERNS_H

#include "common/packet.h"
#include "common/types.h"

#define RULE_MAX            256
#define RULE_NAME_MAX        48
#define RULE_MSG_MAX        128
#define RULE_BYTES_MAX       32

typedef enum {
    SEV_INFO = 0,
    SEV_LOW,
    SEV_MEDIUM,
    SEV_HIGH,
    SEV_CRITICAL,
} severity_t;

/* What a rule matches on. Conditions are ANDed; unset fields are wildcards. */
typedef enum {
    MATCH_NONE         = 0,
    MATCH_TCP_FLAGS    = 1 << 0,   /* tcp.flags & mask == value               */
    MATCH_L4_PORT      = 1 << 1,   /* src or dst port equals `port`           */
    MATCH_IP_TTL_LT    = 1 << 2,   /* ipv4.ttl < `ttl`                        */
    MATCH_BYTE_SIG     = 1 << 3,   /* payload contains the byte signature     */
    MATCH_MALFORMED    = 1 << 4,   /* packet decoded partial/malformed        */
    MATCH_BAD_CHECKSUM = 1 << 5,   /* a verified L3/L4 checksum was wrong      */
} match_flags_t;

/* Parse helpers (also unit-tested directly). */
severity_t severity_from_str(const char *s);   /* unknown → SEV_INFO */
u32        match_token_to_flag(const char *tok);/* unknown → MATCH_NONE */

typedef struct {
    char        name[RULE_NAME_MAX];
    char        message[RULE_MSG_MAX];
    severity_t  severity;
    u32         match;            /* bitwise-OR of match_flags_t              */

    /* Condition operands (interpreted per the bits set in `match`). */
    u8   tcp_flag_mask;
    u8   tcp_flag_value;
    u16  port;
    u8   ttl;
    u8   sig[RULE_BYTES_MAX];     /* byte signature for MATCH_BYTE_SIG        */
    u8   sig_len;

    bool enabled;
} rule_t;

typedef struct {
    rule_t rules[RULE_MAX];
    size_t count;
} ruleset_t;

/* Populate `rs` with the compiled-in built-in rules (always succeeds). */
void patterns_load_builtin(ruleset_t *rs);

/*
 * Append rules parsed from `path` to `rs` (after built-ins, if desired).
 * File format documented in config/patterns.rules. On parse error, logs the
 * offending line and skips it (best-effort). Returns NPA_ERR_IO if unreadable.
 */
npa_result_t patterns_load_file(ruleset_t *rs, const char *path);

/* Human-readable severity label. */
const char *severity_str(severity_t s);

#endif /* NPA_ANALYSIS_PATTERNS_H */
