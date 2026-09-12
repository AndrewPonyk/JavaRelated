/**
 * @file first_fit.c
 * @brief First-fit placement strategy (reference implementation).
 *
 * Walks the address-ordered list and returns the first free block large
 * enough. Fast to search near the head; tends to fragment the low addresses.
 */
#include "strategies/first_fit.h"
#include "core/heap.h"

block_header_t *first_fit_find(size_t size) {
    for (block_header_t *b = heap_first(); b != NULL; b = b->next) {
        if (b->free && b->size >= size) {
            return b;
        }
    }
    return NULL;
}
