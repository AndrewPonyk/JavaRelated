/* test_alerting.c — threshold/hysteresis primitive + per-process engine. */
#include "test_util.h"
#include "ppmon/alerting.h"

static ppmon_proc_metrics_t mkrow(uint32_t pid, double cpu) {
    ppmon_proc_metrics_t m;
    memset(&m, 0, sizeof(m));
    m.pid         = pid;
    m.cpu_percent = cpu;
    strcpy(m.image_name, "p.exe");
    return m;
}

static void test_primitive(void) {
    ppmon_alert_rule_t rules[1] = {
        {.metric = PPMON_METRIC_CPU_PERCENT, .high = 85.0, .low = 70.0, .active = 0}};
    ppmon_alert_event_t ev[4];
    size_t n               = 0;
    ppmon_proc_metrics_t m = mkrow(1234, 50.0);

    CHECK(ppmon_alert_evaluate(rules, 1, &m, ev, 4, &n) == PPMON_OK);
    CHECK_EQ_INT(n, 0); /* below high */

    m.cpu_percent = 90.0;
    CHECK(ppmon_alert_evaluate(rules, 1, &m, ev, 4, &n) == PPMON_OK);
    CHECK_EQ_INT(n, 1);
    CHECK_EQ_INT(ev[0].raised, 1);

    m.cpu_percent = 75.0; /* hysteresis band */
    CHECK(ppmon_alert_evaluate(rules, 1, &m, ev, 4, &n) == PPMON_OK);
    CHECK_EQ_INT(n, 0);

    m.cpu_percent = 60.0; /* below low -> clear */
    CHECK(ppmon_alert_evaluate(rules, 1, &m, ev, 4, &n) == PPMON_OK);
    CHECK_EQ_INT(n, 1);
    CHECK_EQ_INT(ev[0].raised, 0);
}

static void test_engine(void) {
    ppmon_alert_rule_t tmpl[1] = {
        {.metric = PPMON_METRIC_CPU_PERCENT, .high = 85.0, .low = 70.0, .active = 0}};
    ppmon_alert_engine_t *e = NULL;
    CHECK(ppmon_alert_engine_create(tmpl, 1, 8, &e) == PPMON_OK);

    ppmon_alert_event_t ev[16];
    size_t n = 0;

    /* Batch 1: pid 1 crosses high. */
    ppmon_proc_metrics_t b1[1] = {mkrow(1, 90.0)};
    CHECK(ppmon_alert_engine_update(e, b1, 1, ev, 16, &n) == PPMON_OK);
    CHECK_EQ_INT(n, 1);
    CHECK_EQ_INT(ev[0].pid, 1);
    CHECK_EQ_INT(ev[0].raised, 1);

    /* Batch 2: pid 1 holds in the hysteresis band (no event); pid 2 fires.
     * Proves per-PID state independence. */
    ppmon_proc_metrics_t b2[2] = {mkrow(1, 75.0), mkrow(2, 90.0)};
    CHECK(ppmon_alert_engine_update(e, b2, 2, ev, 16, &n) == PPMON_OK);
    CHECK_EQ_INT(n, 1);
    CHECK_EQ_INT(ev[0].pid, 2);
    CHECK_EQ_INT(ev[0].raised, 1);

    /* Batch 3: pid 1 drops below low (cleared); pid 2 disappears entirely and is
     * evicted, which clears its still-active alert too. Both are clears. */
    ppmon_proc_metrics_t b3[1] = {mkrow(1, 60.0)};
    CHECK(ppmon_alert_engine_update(e, b3, 1, ev, 16, &n) == PPMON_OK);
    CHECK_EQ_INT(n, 2);
    CHECK_EQ_INT(ev[0].raised, 0);
    CHECK_EQ_INT(ev[1].raised, 0);
    int pids = ev[0].pid + ev[1].pid; /* {1,2} in some order */
    CHECK_EQ_INT(pids, 3);

    ppmon_alert_engine_destroy(e);
}

int main(void) {
    test_primitive();
    test_engine();
    TEST_MAIN_RETURN();
}
