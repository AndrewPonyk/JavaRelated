/**
 * @file first_fit.h
 * @brief First-fit placement: return the first free block that fits.
 */
#ifndef STRATEGIES_FIRST_FIT_H
#define STRATEGIES_FIRST_FIT_H

#include <stddef.h>
#include "core/block.h"

/** Return the first free block with size >= @p size, or NULL. */
block_header_t *first_fit_find(size_t size);

#endif /* STRATEGIES_FIRST_FIT_H */
