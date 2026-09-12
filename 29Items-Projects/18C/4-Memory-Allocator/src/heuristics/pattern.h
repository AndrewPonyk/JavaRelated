/**
 * @file pattern.h
 * @brief Allocation-pattern heuristics: recommend a strategy from the workload.
 */
#ifndef HEURISTICS_PATTERN_H
#define HEURISTICS_PATTERN_H

#include <stddef.h>
#include "memalloc.h"

/** Record an allocation request size for pattern analysis. */
void pattern_observe(size_t size);

/** Reset the observed-pattern accumulators. */
void pattern_reset(void);

/** Suggest the strategy best suited to the observed workload so far. */
mem_strategy_t pattern_recommend(void);

#endif /* HEURISTICS_PATTERN_H */
