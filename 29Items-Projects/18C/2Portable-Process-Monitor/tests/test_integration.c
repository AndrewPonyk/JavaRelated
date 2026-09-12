/* test_integration.c — live-OS smoke test of the collection layer.
 *
 * Opt-in (registered with CTest only when PPMON_RUN_INTEGRATION=1) because it
 * touches the real kernel/PDH subsystems and is therefore non-deterministic.
 * It asserts only robust invariants, not exact values. */
#include "test_util.h"
#include "ppmon/process_enum.h"
#include "ppmon/metrics.h"
#include "ppmon/pdh_counters.h"
#include "ppmon/sample_store.h"

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#endif

int main(void) {
#if defined(_WIN32)
    uint32_t self = (uint32_t)GetCurrentProcessId();

    /* 1) Enumeration returns processes and includes ourselves. */
    ppmon_enum_t *en = NULL;
    CHECK(ppmon_enum_create(&en) == PPMON_OK);
    CHECK(ppmon_enum_refresh(en) == PPMON_OK);
    size_t count = ppmon_enum_count(en);
    CHECK(count > 0);

    int found_self = 0;
    for (size_t i = 0; i < count; ++i) {
        ppmon_proc_id_t id;
        if (ppmon_enum_at(en, i, &id) == PPMON_OK && id.pid == self) found_self = 1;
    }
    CHECK(found_self);
    ppmon_enum_destroy(en);

    /* 2) PSAPI sampling of our own process yields a non-zero working set. */
    ppmon_sample_t s;
    CHECK(ppmon_metrics_sample(self, &s) == PPMON_OK);
    CHECK(s.working_set_bytes > 0);
    CHECK(s.captured_qpc > 0);

    /* 3) PDH returns a plausible system CPU percentage across two collects. */
    ppmon_pdh_t *pdh = NULL;
    CHECK(ppmon_pdh_open(&pdh) == PPMON_OK);
    ppmon_system_metrics_t sys;
    Sleep(150);
    CHECK(ppmon_pdh_collect(pdh, &sys) == PPMON_OK);
    CHECK(sys.cpu_total_percent >= 0.0 && sys.cpu_total_percent <= 100.0);
    ppmon_pdh_close(pdh);

    /* 4) Two real samples through the store produce a sane CPU% for self. */
    ppmon_store_t *store = NULL;
    CHECK(ppmon_store_create(512, 2, &store) == PPMON_OK);

    LARGE_INTEGER lf;
    QueryPerformanceFrequency(&lf);

    ppmon_store_begin_cycle(store);
    CHECK(ppmon_metrics_sample(self, &s) == PPMON_OK);
    s.pid = self;
    CHECK(ppmon_store_commit(store, &s) == PPMON_OK);

    /* Burn a little CPU so the second sample shows measurable usage. */
    volatile double sink = 0.0;
    for (int i = 0; i < 2000000; ++i)
        sink += (double)i * 0.5;
    Sleep(60);

    ppmon_store_begin_cycle(store);
    CHECK(ppmon_metrics_sample(self, &s) == PPMON_OK);
    s.pid = self;
    CHECK(ppmon_store_commit(store, &s) == PPMON_OK);

    ppmon_proc_metrics_t rows[512];
    size_t n = 0;
    CHECK(ppmon_store_derive(store, lf.QuadPart, 1, rows, 512, &n) == PPMON_OK);

    int found_row = 0;
    for (size_t i = 0; i < n; ++i) {
        if (rows[i].pid == self) {
            found_row = 1;
            CHECK(rows[i].cpu_percent >= 0.0);
            CHECK(rows[i].working_set_bytes > 0);
        }
    }
    CHECK(found_row);
    ppmon_store_destroy(store);
    (void)sink;
#else
    fprintf(stderr, "integration test is Windows-only\n");
#endif
    TEST_MAIN_RETURN();
}
