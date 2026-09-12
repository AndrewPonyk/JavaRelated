/* test_sample_store.c — slot lifecycle + CPU%/rate delta math + eviction. */
#include "test_util.h"
#include "ppmon/sample_store.h"

/* Helper: build a raw sample. */
static ppmon_sample_t mk(uint32_t pid, uint64_t start, uint64_t qpc, uint64_t kern,
                         uint64_t user, uint64_t rd, uint64_t ws) {
    ppmon_sample_t s;
    memset(&s, 0, sizeof(s));
    s.pid               = pid;
    s.start_time_100ns  = start;
    s.captured_qpc      = qpc;
    s.kernel_time_100ns = kern;
    s.user_time_100ns   = user;
    s.read_bytes        = rd;
    s.working_set_bytes = ws;
    strcpy(s.image_name, "a.exe");
    return s;
}

int main(void) {
    ppmon_store_t *s = NULL;
    CHECK(ppmon_store_create(64, 2, &s) == PPMON_OK);
    CHECK(s != NULL);
    CHECK(ppmon_store_create(0, 2, &s) == PPMON_ERR_INVALID_ARG);

    /* freq = 1e7 ticks/sec; one wall second == 1e7 ticks. */
    const int64_t FREQ = 10000000;
    ppmon_proc_metrics_t rows[8];
    size_t n = 0;

    /* Cycle 1: first sample for pid 100 -> no previous, so CPU 0. */
    ppmon_store_begin_cycle(s);
    ppmon_sample_t s1 = mk(100, 5, 0, 0, 0, 0, 0);
    CHECK(ppmon_store_commit(s, &s1) == PPMON_OK);
    CHECK(ppmon_store_derive(s, FREQ, 1, rows, 8, &n) == PPMON_OK);
    CHECK_EQ_INT(n, 1);
    CHECK_NEAR(rows[0].cpu_percent, 0.0, 0.001);

    /* Cycle 2: +1.0 cpu-second over +1.0 wall-second on 1 core -> 100%. */
    ppmon_store_begin_cycle(s);
    ppmon_sample_t s2 = mk(100, 5, FREQ, FREQ /*1e7*100ns = 1s*/, 0, 2000000, 4096);
    CHECK(ppmon_store_commit(s, &s2) == PPMON_OK);
    CHECK(ppmon_store_derive(s, FREQ, 1, rows, 8, &n) == PPMON_OK);
    CHECK_EQ_INT(n, 1);
    CHECK_NEAR(rows[0].cpu_percent, 100.0, 0.01);
    CHECK_EQ_INT(rows[0].read_bytes_per_sec, 2000000);
    CHECK_EQ_INT(rows[0].working_set_bytes, 4096);

    /* Same data, 2 cores -> 50% (derive is a pure read; state unchanged). */
    CHECK(ppmon_store_derive(s, FREQ, 2, rows, 8, &n) == PPMON_OK);
    CHECK_NEAR(rows[0].cpu_percent, 50.0, 0.01);

    /* Cycle 3: pid 100 not committed -> evicted; a reused PID (new start_time)
     * starts a fresh history (CPU 0, not a bogus huge delta). */
    ppmon_store_begin_cycle(s);
    ppmon_sample_t s3 =
        mk(100, 999 /*reused pid, new start*/, 5 * FREQ, 50 * FREQ, 0, 0, 8192);
    CHECK(ppmon_store_commit(s, &s3) == PPMON_OK);
    CHECK(ppmon_store_derive(s, FREQ, 1, rows, 8, &n) == PPMON_OK);
    CHECK_EQ_INT(n, 1);
    CHECK_NEAR(rows[0].cpu_percent, 0.0, 0.001); /* fresh history, no false spike */
    CHECK_EQ_INT(rows[0].working_set_bytes, 8192);

    /* Cycle 4: nothing committed -> the slot is evicted. */
    ppmon_store_begin_cycle(s);
    CHECK(ppmon_store_derive(s, FREQ, 1, rows, 8, &n) == PPMON_OK);
    CHECK_EQ_INT(n, 0);

    ppmon_store_destroy(s);
    TEST_MAIN_RETURN();
}
