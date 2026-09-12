/**
 * @file demo.c
 * @brief User-facing CLI driver for the allocator (the project's "frontend").
 *
 * Usage:
 *   demo [first|best|buddy]
 *
 * Demonstrates allocation, statistics, the allocation-pattern heuristic, and
 * the debug leak snapshot. Everything is freed before exit so the program runs
 * clean under Valgrind / AddressSanitizer.
 */
#include "memalloc.h"
#include "debug/stats.h"
#include "debug/debug.h"
#include "heuristics/pattern.h"

#include <stdio.h>
#include <string.h>

static mem_strategy_t parse_strategy(const char *s) {
    if (s != NULL && strcmp(s, "best") == 0) {
        return MEM_BEST_FIT;
    }
    if (s != NULL && strcmp(s, "buddy") == 0) {
        return MEM_BUDDY;
    }
    return MEM_FIRST_FIT;
}

static const char *strategy_name(mem_strategy_t s) {
    switch (s) {
        case MEM_BEST_FIT: return "best-fit";
        case MEM_BUDDY:    return "buddy";
        default:           return "first-fit";
    }
}

int main(int argc, char **argv) {
    mem_strategy_t strat = parse_strategy(argc > 1 ? argv[1] : "first");

    if (mem_init(strat) != 0) {
        fprintf(stderr, "mem_init(%s) failed — strategy not implemented yet.\n",
                strategy_name(strat));
        return 1;
    }
    printf("== Memory Allocator demo (strategy: %s) ==\n", strategy_name(strat));

    /* A small string payload. */
    char *greeting = mem_malloc(32);
    if (greeting != NULL) {
        snprintf(greeting, 32, "%s", "hello, allocator"); /* portable & bounded */
        printf("payload @%p: \"%s\"\n", (void *)greeting, greeting);
    }

    /* A few allocations of varied sizes; feed the heuristic. */
    void *blocks[5];
    const size_t sizes[5] = {16, 100, 64, 200, 32};
    for (int i = 0; i < 5; i++) {
        blocks[i] = mem_malloc(sizes[i]);
        pattern_observe(sizes[i]);
    }

    /* Free a couple to create holes, then report. */
    mem_free(blocks[1]);
    mem_free(blocks[3]);

    stats_dump();
    printf("heuristic recommends: %s\n", strategy_name(pattern_recommend()));

    /* Debug builds track live allocations; show the current snapshot. */
    printf("-- live allocation snapshot --\n");
    size_t live = debug_report_leaks();
    printf("(%zu live allocations still tracked)\n", live);

    /* Clean up everything so the process exits with no leaks. */
    mem_free(greeting);
    mem_free(blocks[0]);
    mem_free(blocks[2]);
    mem_free(blocks[4]);
    mem_destroy();

    printf("done.\n");
    return 0;
}
