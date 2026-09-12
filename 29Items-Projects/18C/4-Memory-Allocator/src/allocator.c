/**
 * @file allocator.c
 * @brief Top-level façade: a stable public API that dispatches to a strategy.
 *
 * List-based strategies (first/best-fit) share free_list.c and differ only by
 * the injected fit_fn_t. The buddy system is a separate backend selected here.
 */
#include "memalloc.h"

#include "core/heap.h"
#include "core/free_list.h"
#include "strategies/first_fit.h"
#include "strategies/best_fit.h"
#include "strategies/buddy.h"
#include "debug/stats.h"
#include "debug/debug.h"

#include <string.h>

/* memalloc.h turns mem_malloc/mem_free into macros under -DMEM_DEBUG. Undo that
 * inside the implementation so the real functions are defined, not rewritten. */
#ifdef mem_malloc
#  undef mem_malloc
#endif
#ifdef mem_free
#  undef mem_free
#endif

static mem_strategy_t g_strategy = MEM_FIRST_FIT;
static int            g_ready    = 0;

/** Map a list-based strategy to its placement hook. */
static fit_fn_t fit_for(mem_strategy_t s) {
    switch (s) {
        case MEM_BEST_FIT:
            return best_fit_find;
        case MEM_FIRST_FIT:
        default:
            return first_fit_find;
    }
}

int mem_init(mem_strategy_t strategy) {
    if (heap_init() != 0) {
        return -1;
    }
    stats_reset();
    g_strategy = strategy;
    g_ready    = 1;

    if (strategy == MEM_BUDDY) {
        /* TODO: size the buddy arena from configuration / environment. */
        return buddy_init(1u << 20);
    }
    return 0;
}

void mem_destroy(void) {
    if (g_strategy == MEM_BUDDY) {
        buddy_destroy();
    }
    heap_reset();
    g_ready = 0;
}

void *mem_malloc(size_t size) {
    if (!g_ready) {
        mem_init(MEM_FIRST_FIT); /* lazy init with a safe default */
    }
    if (size == 0) {
        return NULL;
    }
    if (g_strategy == MEM_BUDDY) {
        return buddy_alloc(size);
    }
    return free_list_alloc(size, fit_for(g_strategy));
}

void *mem_calloc(size_t nmemb, size_t size) {
    /* Overflow-safe multiply: reject nmemb * size that would wrap. */
    if (nmemb != 0 && size > (size_t)-1 / nmemb) {
        return NULL;
    }
    size_t total = nmemb * size;
    void  *p     = mem_malloc(total);
    if (p != NULL) {
        memset(p, 0, total);
    }
    return p;
}

void *mem_realloc(void *ptr, size_t size) {
    if (ptr == NULL) {
        return mem_malloc(size);
    }
    if (size == 0) {
        mem_free(ptr);
        return NULL;
    }

    block_header_t *b = block_from_payload(ptr);
    if (!block_is_valid(b)) {
        return NULL;
    }
    if (b->size >= size) {
        return ptr; /* fits in place. TODO: split the tail to reclaim slack. */
    }

    void *np = mem_malloc(size);
    if (np == NULL) {
        return NULL;
    }
    memcpy(np, ptr, b->size);
    mem_free(ptr);
    return np;
}

void mem_free(void *ptr) {
    if (g_strategy == MEM_BUDDY) {
        buddy_free(ptr);
        return;
    }
    free_list_free(ptr);
}

void mem_set_strategy(mem_strategy_t strategy) { g_strategy = strategy; }

mem_strategy_t mem_get_strategy(void) { return g_strategy; }

void *mem_malloc_dbg(size_t size, const char *file, int line) {
    void *p = mem_malloc(size);
    debug_track_alloc(p, size, file, line);
    return p;
}

void mem_free_dbg(void *ptr, const char *file, int line) {
    debug_track_free(ptr, file, line);
    mem_free(ptr);
}
