/*
 * kheap.c — Kernel heap allocator.
 *
 * A first-fit free-list allocator over a fixed virtual window. Each block has a
 * header; freed blocks coalesce with neighbors. Good enough for kernel objects;
 * a slab cache for hot fixed-size types is the planned optimization.
 */
#include "../include/kernel.h"
#include "../include/memory.h"

typedef struct block_header {
    size_t               size;     /* usable bytes (excludes header) */
    bool                 free;
    struct block_header *next;
    struct block_header *prev;
} block_header_t;

#define HDR_SIZE   (sizeof(block_header_t))
#define ALIGN(n)   (((n) + 15) & ~((size_t)15))

static block_header_t *heap_head;
static uintptr_t       heap_end;

void kheap_init(uintptr_t start, size_t size)
{
    heap_head        = (block_header_t *)start;
    heap_head->size  = size - HDR_SIZE;
    heap_head->free  = true;
    heap_head->next  = NULL;
    heap_head->prev  = NULL;
    heap_end         = start + size;
    KLOG_DEBUG("kheap: %lu KiB at %p", (unsigned long)(size >> 10), (void *)start);
}

static void split_block(block_header_t *b, size_t need)
{
    if (b->size < need + HDR_SIZE + 16) return;        /* not worth splitting */
    block_header_t *nb = (block_header_t *)((uintptr_t)(b + 1) + need);
    nb->size = b->size - need - HDR_SIZE;
    nb->free = true;
    nb->next = b->next;
    nb->prev = b;
    if (b->next) b->next->prev = nb;
    b->next  = nb;
    b->size  = need;
}

void *kmalloc(size_t size)
{
    size = ALIGN(size);
    for (block_header_t *b = heap_head; b; b = b->next) {
        if (b->free && b->size >= size) {
            split_block(b, size);
            b->free = false;
            return (void *)(b + 1);
        }
    }
    /* Fixed-size heap window: callers must handle NULL. (Growing the heap by
     * mapping more frames via the VMM is the documented next step.) */
    KLOG_ERROR("kheap: OOM requesting %lu bytes", (unsigned long)size);
    return NULL;
}

void *kcalloc(size_t n, size_t size)
{
    size_t total;
    if (__builtin_mul_overflow(n, size, &total)) return NULL;   /* overflow */
    void  *p = kmalloc(total);
    if (p) {
        uint8_t *bytes = (uint8_t *)p;
        for (size_t i = 0; i < total; i++) bytes[i] = 0;
    }
    return p;
}

void kfree(void *ptr)
{
    if (!ptr) return;
    block_header_t *b = (block_header_t *)ptr - 1;
    b->free = true;
    /* Coalesce forward and backward. */
    if (b->next && b->next->free) {
        b->size += HDR_SIZE + b->next->size;
        b->next  = b->next->next;
        if (b->next) b->next->prev = b;
    }
    if (b->prev && b->prev->free) {
        b->prev->size += HDR_SIZE + b->size;
        b->prev->next  = b->next;
        if (b->next) b->next->prev = b->prev;
    }
}
