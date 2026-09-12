/**
 * @file buddy.h
 * @brief Binary buddy-system allocator — a self-contained alternative backend.
 *
 * Unlike first/best-fit (which share the free_list engine), the buddy system
 * manages its own arena and one free list per power-of-two "order", so it lives
 * behind the same façade but does not use block_header_t / free_list.c.
 */
#ifndef STRATEGIES_BUDDY_H
#define STRATEGIES_BUDDY_H

#include <stddef.h>

/** Initialise the buddy arena. Returns 0 on success, non-zero otherwise. */
int buddy_init(size_t arena_size);

/** Release the buddy arena. */
void buddy_destroy(void);

/** Allocate @p size bytes (rounded up to a power of two). NULL on failure. */
void *buddy_alloc(size_t size);

/** Free a pointer previously returned by buddy_alloc. */
void buddy_free(void *ptr);

#endif /* STRATEGIES_BUDDY_H */
