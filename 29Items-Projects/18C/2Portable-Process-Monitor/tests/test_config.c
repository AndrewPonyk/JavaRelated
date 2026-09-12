/* test_config.c — defaults, validation, INI parsing, and CLI overrides. */
#include "test_util.h"
#include "ppmon/config.h"
#include <stdio.h>

static void test_defaults_and_validation(void) {
    ppmon_config_t cfg;
    ppmon_config_defaults(&cfg);

    CHECK_EQ_INT(cfg.interval_ms, 1000);
    CHECK_EQ_INT(cfg.top_n, 15);
    CHECK_EQ_INT(cfg.listen_port, 9555);
    CHECK_EQ_STR(cfg.listen_addr, "127.0.0.1");
    CHECK(cfg.cpu_alert_low < cfg.cpu_alert_high);
    CHECK(ppmon_config_validate(&cfg) == PPMON_OK);

    cfg.interval_ms = 10; /* below the 50ms floor */
    CHECK(ppmon_config_validate(&cfg) == PPMON_ERR_INVALID_ARG);
    cfg.interval_ms = 1000;

    cfg.cpu_alert_low  = 90.0; /* inverted hysteresis */
    cfg.cpu_alert_high = 80.0;
    CHECK(ppmon_config_validate(&cfg) == PPMON_ERR_INVALID_ARG);
}

static void test_ini(void) {
    const char *path = "ppmon_test_cfg.ini";
    FILE *fp         = fopen(path, "w");
    CHECK(fp != NULL);
    if (fp) {
        fputs("; sample config\n"
              "[polling]\n"
              "interval_ms = 250\n"
              "top_n = 7\n"
              "[output]\n"
              "csv_enabled = true\n"
              "csv_path = out.csv\n"
              "[network]\n"
              "listen_port = 6000\n"
              "[runtime]\n"
              "log_level = 1\n",
              fp);
        fclose(fp);
    }

    ppmon_config_t cfg;
    ppmon_config_defaults(&cfg);
    CHECK(ppmon_config_load_ini(&cfg, path) == PPMON_OK);
    CHECK_EQ_INT(cfg.interval_ms, 250);
    CHECK_EQ_INT(cfg.top_n, 7);
    CHECK_EQ_INT(cfg.csv_enabled, 1);
    CHECK_EQ_STR(cfg.csv_path, "out.csv");
    CHECK_EQ_INT(cfg.listen_port, 6000);
    CHECK_EQ_INT(cfg.log_level, 1);
    remove(path);

    /* A missing file is not an error: defaults stand. */
    ppmon_config_t cfg2;
    ppmon_config_defaults(&cfg2);
    CHECK(ppmon_config_load_ini(&cfg2, "does_not_exist.ini") == PPMON_OK);
    CHECK_EQ_INT(cfg2.interval_ms, 1000);
}

static void test_cli(void) {
    ppmon_config_t cfg;
    ppmon_config_defaults(&cfg);

    char *argv[] = {"ppmon", "--interval", "2000",     "--top",
                    "5",     "--once",     "--listen", "127.0.0.1:7000"};
    int argc     = (int)(sizeof(argv) / sizeof(argv[0]));
    CHECK(ppmon_config_apply_args(&cfg, argc, argv) == PPMON_OK);
    CHECK_EQ_INT(cfg.interval_ms, 2000);
    CHECK_EQ_INT(cfg.top_n, 5);
    CHECK_EQ_INT(cfg.run_once, 1);
    CHECK_EQ_INT(cfg.net_enabled, 1);
    CHECK_EQ_INT(cfg.listen_port, 7000);

    /* Unknown option is rejected. */
    ppmon_config_t bad;
    ppmon_config_defaults(&bad);
    char *argv_bad[] = {"ppmon", "--nope"};
    CHECK(ppmon_config_apply_args(&bad, 2, argv_bad) == PPMON_ERR_INVALID_ARG);

    /* Missing value is rejected. */
    ppmon_config_t bad2;
    ppmon_config_defaults(&bad2);
    char *argv_bad2[] = {"ppmon", "--interval"};
    CHECK(ppmon_config_apply_args(&bad2, 2, argv_bad2) == PPMON_ERR_INVALID_ARG);
}

int main(void) {
    test_defaults_and_validation();
    test_ini();
    test_cli();
    TEST_MAIN_RETURN();
}
