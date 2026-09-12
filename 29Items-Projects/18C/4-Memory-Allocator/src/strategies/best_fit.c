/**
 * @file best_fit.c
 * @brief Best-fit placement strategy.
 *
 * Scans the whole list for the tightest-fitting free block. Minimizes leftover
 * waste per allocation at the cost of a full O(n) scan; can leave many tiny
 * unusable slivers over time (the classic best-fit downside).
 */
#include "strategies/best_fit.h"
#include "core/heap.h"

block_header_t *best_fit_find(size_t size) {
    block_header_t *best = NULL;

    for (block_header_t *b = heap_first(); b != NULL; b = b->next) {
        if (b->free && b->size >= size) {
            if (best == NULL || b->size < best->size) {
                best = b;
                if (b->size == size) {
                    break; /* perfect fit — cannot do better */
                }
            }
        }
    }
    return best;
}
