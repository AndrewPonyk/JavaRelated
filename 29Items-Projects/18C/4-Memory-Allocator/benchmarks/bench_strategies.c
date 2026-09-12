/**
 * @file bench_strategies.c
 * @brief Throughput comparison across placement strategies.
 *
 * Uses a bounded working set (free a random slot, allocate a new one) so live
 * memory stays small and the numbers reflect placement cost, not heap growth.
 * Build optimised: `make bench`.
 */
#include "memalloc.h"
#include "debug/stats.h"

#include <stdio.h>
#include <stdlib.h>
#include <time.h>

#define WORKING_SET 4096
#define OPS         200000
#define MAX_SIZE    256

static void bench(mem_strategy_t s, const char *name) {
    if (mem_init(s) != 0) {
        printf("%-10s: unavailable (not implemented)\n", name);
        return;
    }

    static void *slots[WORKING_SET];
    for (int i = 0; i < WORKING_SET; i++) {
        slots[i] = NULL;
    }

    clock_t t0 = clock();
    for (long i = 0; i < OPS; i++) {
        int idx = rand() % WORKING_SET;
        if (slots[idx] != NULL) {
            mem_free(slots[idx]);
        }
        size_t sz  = (size_t)(rand() % MAX_SIZE) + 1;
        slots[idx] = mem_malloc(sz);
    }
    clock_t t1 = clock();

    double frag = stats_fragmentation(); /* at steady-state working set */

    for (int i = 0; i < WORKING_SET; i++) {
        if (slots[i] != NULL) {
            mem_free(slots[i]);
        }
    }

    double secs = (double)(t1 - t0) / CLOCKS_PER_SEC;
    printf("%-10s: %ld ops in %6.3fs  (%.2f Mops/s)  frag=%.1f%%\n",
           name, (long)OPS, secs,
           secs > 0.0 ? (double)OPS / secs / 1e6 : 0.0, frag * 100.0);

    mem_destroy();
}

int main(void) {
    printf("Allocator throughput — working set=%d, ops=%d, sizes 1..%d\n\n",
           WORKING_SET, OPS, MAX_SIZE);

    srand(42);
    bench(MEM_FIRST_FIT, "first-fit");
    srand(42);
    bench(MEM_BEST_FIT, "best-fit");
    srand(42);
    bench(MEM_BUDDY, "buddy"); /* prints "unavailable" until implemented */

    return 0;
}
