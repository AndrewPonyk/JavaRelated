/* =============================================================================
 *  memory.c  --  Freestanding mem* helpers + a first-fit free-list heap
 *
 *  The heap is a doubly-implicit free list of blocks, each prefixed with a
 *  small header. kmalloc does first-fit with block splitting; kfree marks a
 *  block free and coalesces with the following block. Simple and adequate for
 *  a teaching kernel; the PMM (pmm.c) will later back this with real pages.
 * ===========================================================================*/
#include "../include/memory.h"

/* ---------------- raw memory helpers (also host-unit-tested) -------------- */
void *memset(void *dest, int value, size_t count) {
    u8 *d = (u8 *)dest;
    while (count--) *d++ = (u8)value;
    return dest;
}

void *memcpy(void *dest, const void *src, size_t count) {
    u8 *d = (u8 *)dest;
    const u8 *s = (const u8 *)src;
    while (count--) *d++ = *s++;
    return dest;
}

void *memmove(void *dest, const void *src, size_t count) {
    u8 *d = (u8 *)dest;
    const u8 *s = (const u8 *)src;
    if (d < s) {
        while (count--) *d++ = *s++;            /* copy forward */
    } else if (d > s) {
        d += count; s += count;                 /* copy backward (overlap) */
        while (count--) *--d = *--s;
    }
    return dest;
}

/* ---------------------------- heap allocator ----------------------------- */
typedef struct block_header {
    size_t               size;      /* usable bytes after this header */
    bool                 free;
    struct block_header *next;
} block_header_t;

#define HEADER_SIZE  sizeof(block_header_t)
#define ALIGN4(x)    (((x) + 3u) & ~3u)

static block_header_t *heap_head = NULL;

void heap_init_at(void *base, size_t size) {
    heap_head = (block_header_t *)base;
    heap_head->size = size - HEADER_SIZE;
    heap_head->free = true;
    heap_head->next = NULL;
}

void heap_init(void) {
    heap_init_at((void *)KHEAP_START, KHEAP_SIZE);
}

/* Split `block` so it holds exactly `size` bytes, putting the remainder into a
 * new free block -- but only if the remainder can hold a header plus a little. */
static void split_block(block_header_t *block, size_t size) {
    if (block->size < size + HEADER_SIZE + 4) return;   /* not worth splitting */

    block_header_t *rest =
        (block_header_t *)((u8 *)block + HEADER_SIZE + size);
    rest->size = block->size - size - HEADER_SIZE;
    rest->free = true;
    rest->next = block->next;

    block->size = size;
    block->next = rest;
}

void *kmalloc(size_t size) {
    if (!heap_head || size == 0) return NULL;
    size = ALIGN4(size);

    for (block_header_t *b = heap_head; b != NULL; b = b->next) {
        if (b->free && b->size >= size) {       /* first fit */
            split_block(b, size);
            b->free = false;
            return (u8 *)b + HEADER_SIZE;
        }
    }
    return NULL;                                 /* exhausted: recoverable */
}

void kfree(void *ptr) {
    if (!ptr) return;
    block_header_t *block = (block_header_t *)((u8 *)ptr - HEADER_SIZE);
    block->free = true;

    /* Coalesce ALL adjacent free blocks. Blocks are only ever split forward and
     * never reordered, so the list stays in ascending address order; one pass
     * therefore merges the freed block with both its predecessor and successor.
     * This fully defragments the heap on every free. */
    for (block_header_t *b = heap_head; b && b->next; ) {
        if (b->free && b->next->free) {
            b->size += HEADER_SIZE + b->next->size;
            b->next  = b->next->next;       /* re-check the now-larger block */
        } else {
            b = b->next;
        }
    }
}

size_t heap_bytes_free(void) {
    size_t total = 0;
    for (block_header_t *b = heap_head; b != NULL; b = b->next) {
        if (b->free) total += b->size;
    }
    return total;
}
