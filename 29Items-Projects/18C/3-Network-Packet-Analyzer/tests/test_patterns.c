/* SPDX-License-Identifier: MIT
 *
 * tests/test_patterns.c — rule-file parser + helpers.
 */
#include "test_common.h"

#include "analysis/patterns.h"

#include <stdio.h>
#include <string.h>

#define TMP_RULES "test_patterns_tmp.rules"

static rule_t *find(ruleset_t *rs, const char *name) {
    for (size_t i = 0; i < rs->count; ++i)
        if (strcmp(rs->rules[i].name, name) == 0) return &rs->rules[i];
    return NULL;
}

static void test_helpers(void) {
    ASSERT_EQ_INT(SEV_HIGH, severity_from_str("high"));
    ASSERT_EQ_INT(SEV_CRITICAL, severity_from_str("critical"));
    ASSERT_EQ_INT(SEV_INFO, severity_from_str("bogus"));
    ASSERT_EQ_UINT(MATCH_TCP_FLAGS, match_token_to_flag("tcp_flags"));
    ASSERT_EQ_UINT(MATCH_BYTE_SIG, match_token_to_flag("byte_sig"));
    ASSERT_EQ_UINT(MATCH_BAD_CHECKSUM, match_token_to_flag("bad_checksum"));
    ASSERT_EQ_UINT(MATCH_NONE, match_token_to_flag("nope"));
}

static void test_parse_rules(void) {
    FILE *f = fopen(TMP_RULES, "w");
    ASSERT_TRUE(f != NULL);
    fprintf(f, "# a comment\n\n");
    fprintf(f, "name=t-xmas; sev=medium; match=tcp_flags; mask=0x29; value=0x29; msg=xmas\n");
    fprintf(f, "name=ftp-pass; sev=high; match=byte_sig,l4_port; port=21; sig=\"PASS \"; msg=cleartext\n");
    fclose(f);

    ruleset_t rs;
    rs.count = 0;
    ASSERT_EQ_INT(NPA_OK, patterns_load_file(&rs, TMP_RULES));
    ASSERT_EQ_UINT(2u, rs.count);

    rule_t *x = find(&rs, "t-xmas");
    ASSERT_TRUE(x != NULL);
    ASSERT_EQ_INT(SEV_MEDIUM, x->severity);
    ASSERT_TRUE((x->match & MATCH_TCP_FLAGS) != 0);
    ASSERT_EQ_UINT(0x29u, x->tcp_flag_mask);
    ASSERT_EQ_UINT(0x29u, x->tcp_flag_value);

    rule_t *p = find(&rs, "ftp-pass");
    ASSERT_TRUE(p != NULL);
    ASSERT_EQ_INT(SEV_HIGH, p->severity);
    ASSERT_TRUE((p->match & MATCH_BYTE_SIG) != 0);
    ASSERT_TRUE((p->match & MATCH_L4_PORT) != 0);
    ASSERT_EQ_UINT(21u, p->port);
    ASSERT_EQ_UINT(5u, p->sig_len);
    ASSERT_TRUE(memcmp(p->sig, "PASS ", 5) == 0);

    remove(TMP_RULES);
}

static void test_upsert_by_name(void) {
    ruleset_t rs;
    patterns_load_builtin(&rs);
    size_t before = rs.count;

    FILE *f = fopen(TMP_RULES, "w");
    fprintf(f, "name=tcp-xmas-scan; sev=critical; match=tcp_flags; mask=0x29; value=0x29\n");
    fprintf(f, "name=brand-new; sev=low; match=malformed\n");
    fclose(f);

    patterns_load_file(&rs, TMP_RULES);
    ASSERT_EQ_UINT(before + 1, rs.count);   /* one overridden, one added */

    rule_t *x = find(&rs, "tcp-xmas-scan");
    ASSERT_TRUE(x != NULL);
    ASSERT_EQ_INT(SEV_CRITICAL, x->severity);  /* overridden */
    ASSERT_TRUE(find(&rs, "brand-new") != NULL);

    remove(TMP_RULES);
}

static void test_skips_invalid(void) {
    FILE *f = fopen(TMP_RULES, "w");
    fprintf(f, "this line has no equals and is skipped\n");
    fprintf(f, "sev=high; match=malformed\n");           /* no name → skipped */
    fprintf(f, "name=ok-rule; match=malformed; msg=fine\n");
    fclose(f);

    ruleset_t rs;
    rs.count = 0;
    patterns_load_file(&rs, TMP_RULES);
    ASSERT_EQ_UINT(1u, rs.count);
    ASSERT_TRUE(find(&rs, "ok-rule") != NULL);
    remove(TMP_RULES);
}

int main(void) {
    printf("patterns tests:\n");
    RUN_TEST(test_helpers);
    RUN_TEST(test_parse_rules);
    RUN_TEST(test_upsert_by_name);
    RUN_TEST(test_skips_invalid);
    return test_summary("patterns");
}
