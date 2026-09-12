/**
 * @file best_fit.h
 * @brief Best-fit placement: return the smallest free block that fits.
 */
#ifndef STRATEGIES_BEST_FIT_H
#define STRATEGIES_BEST_FIT_H

#include <stddef.h>
#include "core/block.h"

/** Return the smallest free block with size >= @p size, or NULL. */
block_header_t *best_fit_find(size_t size);

#endif /* STRATEGIES_BEST_FIT_H */
