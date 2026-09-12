/**
 * @file pattern.c
 * @brief Allocation-pattern heuristics (teaching scaffold).
 *
 * Observes the stream of request sizes and recommends a placement strategy:
 *   - Mostly power-of-two sizes -> buddy system (its sweet spot).
 *   - Small, varied sizes       -> best-fit (minimise per-request waste).
 *   - Few large, long-lived     -> first-fit (cheapest search).
 *
 * TODO: replace the running summary with a proper size histogram and a decay
 *       window so the recommendation adapts to phase changes in the workload.
 */
#include "heuristics/pattern.h"

static size_t g_count;
static size_t g_sum;
static size_t g_pow2_hits;

static int is_pow2(size_t x) {
    return x != 0 && (x & (x - 1)) == 0;
}

void pattern_observe(size_t size) {
    g_count += 1;
    g_sum += size;
    if (is_pow2(size)) {
        g_pow2_hits += 1;
    }
}

void pattern_reset(void) {
    g_count     = 0;
    g_sum       = 0;
    g_pow2_hits = 0;
}

mem_strategy_t pattern_recommend(void) {
    if (g_count == 0) {
        return MEM_FIRST_FIT;
    }

    /* Majority of requests are powers of two: buddy shines. */
    if (g_pow2_hits * 2 >= g_count) {
        return MEM_BUDDY;
    }

    size_t avg = g_sum / g_count;
    if (avg <= 128) {
        return MEM_BEST_FIT; /* many small, varied sizes */
    }
    return MEM_FIRST_FIT; /* fewer, larger blocks */
}
