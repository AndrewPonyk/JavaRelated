/**
 * @file memalloc.h
 * @brief Public API for the educational memory allocator.
 *
 * The allocator supports multiple placement strategies (first-fit, best-fit,
 * buddy system) selectable at runtime, plus optional debug instrumentation
 * (leak detection, double-free detection, buffer-overflow canaries).
 *
 * This is the ONLY header that consumers of the library should include.
 */
#ifndef MEMALLOC_H
#define MEMALLOC_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/** Placement strategy used to satisfy allocation requests. */
typedef enum mem_strategy {
    MEM_FIRST_FIT = 0, /**< First block large enough. O(n) search.         */
    MEM_BEST_FIT,      /**< Smallest block large enough. Less waste, O(n). */
    MEM_BUDDY          /**< Power-of-two buddy system. O(log n), low frag. */
} mem_strategy_t;

/**
 * Initialise the allocator. Must be called before any allocation.
 * @return 0 on success, non-zero on failure.
 */
int mem_init(mem_strategy_t strategy);

/** Tear down the allocator and release/forget all backing memory. */
void mem_destroy(void);

/** Allocate @p size bytes. Returns NULL on failure or size 0. */
void *mem_malloc(size_t size);

/** Allocate and zero @p nmemb * @p size bytes. NULL on overflow/failure. */
void *mem_calloc(size_t nmemb, size_t size);

/**
 * Resize the allocation at @p ptr to @p size bytes.
 * realloc(NULL, n) behaves like malloc(n); realloc(p, 0) frees p and returns NULL.
 */
void *mem_realloc(void *ptr, size_t size);

/** Release a block previously returned by mem_malloc/mem_calloc/mem_realloc. */
void mem_free(void *ptr);

/** Switch placement strategy at runtime (affects subsequent allocations). */
void mem_set_strategy(mem_strategy_t strategy);

/** Return the active placement strategy. */
mem_strategy_t mem_get_strategy(void);

/* --- Debug-instrumented variants: record the call site ------------------- */
/* These are wired up automatically by the macros below when built with
 * -DMEM_DEBUG. Call them directly only if you need explicit site info.      */
void *mem_malloc_dbg(size_t size, const char *file, int line);
void  mem_free_dbg(void *ptr, const char *file, int line);

/* NOTE: the macros are defined AFTER the prototypes above on purpose, so the
 * prototypes themselves are not rewritten by the function-like macro. */
#ifdef MEM_DEBUG
#  define mem_malloc(sz) mem_malloc_dbg((sz), __FILE__, __LINE__)
#  define mem_free(p)    mem_free_dbg((p), __FILE__, __LINE__)
#endif

#ifdef __cplusplus
}
#endif

#endif /* MEMALLOC_H */
