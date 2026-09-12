/**
 * @file buddy.c
 * @brief Binary buddy-system allocator (SCAFFOLD — see Phase 2 TODOs).
 *
 * Algorithm outline (for the implementer):
 *   - Reserve one contiguous arena of size 2^MAX_ORDER.
 *   - Maintain free_lists[order]; each node is a free block of size 2^order.
 *   - alloc(n): order = ceil(log2(n)); find the smallest non-empty list >= order;
 *               repeatedly split the block in half, pushing the unused buddy onto
 *               the next-lower list, until the target order is reached.
 *   - free(p):  compute the block's order and its buddy address (addr XOR size);
 *               while the buddy is free and same order, remove it and merge up.
 *
 * Buddy gives O(log n) alloc/free and zero external fragmentation at the cost of
 * internal fragmentation (rounding every request up to a power of two).
 */
#include "strategies/buddy.h"

#include <stddef.h>
#include <stdint.h>

/** 16-byte minimum block (2^4). */
#define BUDDY_MIN_ORDER 4
/** 16 MiB maximum arena (2^24). */
#define BUDDY_MAX_ORDER 24

/* TODO: free_lists[BUDDY_MAX_ORDER + 1], arena base, and a split bitmap. */

int buddy_init(size_t arena_size) {
    (void)arena_size;
    /* TODO: reserve the arena via heap_extend/mmap and seed the top-order list. */
    return -1; /* not yet implemented */
}

void buddy_destroy(void) {
    /* TODO: release the arena and clear the free lists. */
}

void *buddy_alloc(size_t size) {
    (void)size;
    /* TODO: round up to power of two, locate smallest free order, split down. */
    return NULL;
}

void buddy_free(void *ptr) {
    (void)ptr;
    /* TODO: derive order from address, merge with buddy while it stays free. */
}
