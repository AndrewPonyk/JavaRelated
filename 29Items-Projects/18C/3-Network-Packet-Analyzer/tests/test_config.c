/* SPDX-License-Identifier: MIT
 *
 * tests/test_config.c — config-file parsing, env overrides, validation.
 */
#include "test_common.h"

#include "util/config.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define TMP_CONF "test_config_tmp.conf"

static void write_conf(const char *path) {
    FILE *f = fopen(path, "w");
    fprintf(f, "# test config\n\n");
    fprintf(f, "interface = eth5\n");
    fprintf(f, "snaplen = 1500\n");
    fprintf(f, "ring_size = 4096\n");
    fprintf(f, "promiscuous = false\n");
    fprintf(f, "headless = true\n");
    fprintf(f, "log_level = debug\n");
    fprintf(f, "bpf_filter = tcp port 443\n");
    fclose(f);
}

static void test_load_file(void) {
    write_conf(TMP_CONF);
    npa_config_t c;
    config_defaults(&c);
    ASSERT_EQ_INT(NPA_OK, config_load_file(&c, TMP_CONF));

    ASSERT_STR_EQ("eth5", c.interface);
    ASSERT_EQ_INT(1500, c.snaplen);
    ASSERT_EQ_UINT(4096u, c.ring_size);
    ASSERT_FALSE(c.promiscuous);
    ASSERT_TRUE(c.headless);
    ASSERT_EQ_INT(LOG_DEBUG, c.log_level);
    ASSERT_STR_EQ("tcp port 443", c.bpf_filter);

    remove(TMP_CONF);
}

static void test_missing_file(void) {
    npa_config_t c;
    config_defaults(&c);
    ASSERT_EQ_INT(NPA_ERR_NOTFOUND, config_load_file(&c, "definitely-not-here.conf"));
}

static void test_env_override(void) {
    npa_config_t c;
    config_defaults(&c);
    setenv("NPA_SNAPLEN", "999", 1);
    setenv("NPA_INTERFACE", "wlan0", 1);
    config_apply_env(&c);
    ASSERT_EQ_INT(999, c.snaplen);
    ASSERT_STR_EQ("wlan0", c.interface);
    unsetenv("NPA_SNAPLEN");
    unsetenv("NPA_INTERFACE");
}

static void test_validate(void) {
    npa_config_t c;

    config_defaults(&c);
    c.interface = "eth0"; c.pcap_file = "x.pcap";
    ASSERT_EQ_INT(NPA_ERR_INVAL, config_validate(&c));   /* both set */

    config_defaults(&c);
    ASSERT_EQ_INT(NPA_ERR_INVAL, config_validate(&c));   /* neither set */

    config_defaults(&c);
    c.interface = "eth0";
    ASSERT_EQ_INT(NPA_OK, config_validate(&c));

    config_defaults(&c);
    c.pcap_file = "x.pcap"; c.snaplen = 0;
    ASSERT_EQ_INT(NPA_ERR_INVAL, config_validate(&c));   /* bad snaplen */
}

int main(void) {
    printf("config tests:\n");
    RUN_TEST(test_load_file);
    RUN_TEST(test_missing_file);
    RUN_TEST(test_env_override);
    RUN_TEST(test_validate);
    return test_summary("config");
}
