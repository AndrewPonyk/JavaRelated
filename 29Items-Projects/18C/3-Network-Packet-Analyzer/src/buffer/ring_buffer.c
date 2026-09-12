/* SPDX-License-Identifier: MIT
 *
 * buffer/ring_buffer.c — mutex + condvar bounded queue.
 *
 * This is a complete, working implementation: it is the one piece
 * that must be correct before anything else, and it is fully unit-tested in
 * tests/test_ring_buffer.c. Phase 3 may replace the internals with a lock-free
 * ring; the public contract stays the same.
 */
#include "buffer/ring_buffer.h"

#include <stdlib.h>
#include <string.h>

/* Round v up to the next power of two (>= 2). */
static size_t next_pow2(size_t v) {
    size_t p = 2;
    while (p < v) {
        p <<= 1;
        if (p == 0) return SIZE_MAX;     /* overflow guard */
    }
    return p;
}

npa_result_t ring_create(ring_buffer_t **out, size_t min_capacity) {
    if (!out || min_capacity < 2) return NPA_ERR_INVAL;

    ring_buffer_t *rb = calloc(1, sizeof *rb);
    if (!rb) return NPA_ERR_NOMEM;

    rb->capacity = next_pow2(min_capacity);
    rb->mask     = rb->capacity - 1;
    rb->slots    = calloc(rb->capacity, sizeof *rb->slots);
    if (!rb->slots) {
        free(rb);
        return NPA_ERR_NOMEM;
    }

    pthread_mutex_init(&rb->lock, NULL);
    pthread_cond_init(&rb->not_empty, NULL);
    pthread_cond_init(&rb->not_full, NULL);
    atomic_init(&rb->closed, false);
    atomic_init(&rb->enq_count, 0);
    atomic_init(&rb->deq_count, 0);
    atomic_init(&rb->drop_count, 0);

    *out = rb;
    return NPA_OK;
}

void ring_destroy(ring_buffer_t *rb) {
    if (!rb) return;
    pthread_mutex_destroy(&rb->lock);
    pthread_cond_destroy(&rb->not_empty);
    pthread_cond_destroy(&rb->not_full);
    free(rb->slots);
    free(rb);
}

static bool is_full_locked(const ring_buffer_t *rb) {
    return (rb->head - rb->tail) == rb->capacity;
}

static bool is_empty_locked(const ring_buffer_t *rb) {
    return rb->head == rb->tail;
}

npa_result_t ring_push(ring_buffer_t *rb, const captured_frame_t *frame,
                       bool block) {
    if (!rb || !frame) return NPA_ERR_INVAL;

    pthread_mutex_lock(&rb->lock);

    while (is_full_locked(rb)) {
        if (atomic_load_explicit(&rb->closed, memory_order_acquire)) {
            pthread_mutex_unlock(&rb->lock);
            return NPA_ERR_AGAIN;
        }
        if (!block) {
            pthread_mutex_unlock(&rb->lock);
            return NPA_ERR_FULL;
        }
        pthread_cond_wait(&rb->not_full, &rb->lock);  /* predicate re-checked */
    }

    /* Copy in: capture must detach from libpcap's transient buffer. */
    memcpy(&rb->slots[rb->head & rb->mask], frame, sizeof *frame);
    rb->head++;
    atomic_fetch_add_explicit(&rb->enq_count, 1, memory_order_relaxed);

    pthread_cond_signal(&rb->not_empty);
    pthread_mutex_unlock(&rb->lock);
    return NPA_OK;
}

npa_result_t ring_push_drop(ring_buffer_t *rb, const captured_frame_t *frame) {
    npa_result_t r = ring_push(rb, frame, false);
    if (r == NPA_ERR_FULL) {
        atomic_fetch_add_explicit(&rb->drop_count, 1, memory_order_relaxed);
    }
    return r;
}

npa_result_t ring_pop(ring_buffer_t *rb, captured_frame_t *out) {
    if (!rb || !out) return NPA_ERR_INVAL;

    pthread_mutex_lock(&rb->lock);

    while (is_empty_locked(rb)) {
        if (atomic_load_explicit(&rb->closed, memory_order_acquire)) {
            pthread_mutex_unlock(&rb->lock);
            return NPA_ERR_AGAIN;        /* closed and drained → consumer exits */
        }
        pthread_cond_wait(&rb->not_empty, &rb->lock);
    }

    memcpy(out, &rb->slots[rb->tail & rb->mask], sizeof *out);
    rb->tail++;
    atomic_fetch_add_explicit(&rb->deq_count, 1, memory_order_relaxed);

    pthread_cond_signal(&rb->not_full);
    pthread_mutex_unlock(&rb->lock);
    return NPA_OK;
}

void ring_close(ring_buffer_t *rb) {
    if (!rb) return;
    pthread_mutex_lock(&rb->lock);
    atomic_store_explicit(&rb->closed, true, memory_order_release);
    pthread_cond_broadcast(&rb->not_empty);
    pthread_cond_broadcast(&rb->not_full);
    pthread_mutex_unlock(&rb->lock);
}

size_t ring_size(const ring_buffer_t *rb) {
    if (!rb) return 0;
    /* Advisory: head/tail read without the lock. Good enough for a UI gauge. */
    return rb->head - rb->tail;
}

u64 ring_dropped(const ring_buffer_t *rb) {
    if (!rb) return 0;
    return atomic_load_explicit((atomic_uint_fast64_t *)&rb->drop_count,
                                memory_order_relaxed);
}

size_t ring_capacity(const ring_buffer_t *rb) {
    return rb ? rb->capacity : 0;
}
