/* SPDX-License-Identifier: MIT
 *
 * buffer/ring_buffer.h — bounded, thread-safe queue of captured frames.
 *
 * Producer: the capture thread (one). Consumer(s): analyzer thread(s).
 * Phase 1 is a single-producer/single-consumer mutex+condvar ring; the API is
 * written so it can be promoted to a lock-free ring later without callers
 * changing (see TECH-NOTES / ARCHITECTURE §2.4).
 *
 * Capacity is rounded up to a power of two so index wrap is a mask, not a mod.
 */
#ifndef NPA_BUFFER_RING_BUFFER_H
#define NPA_BUFFER_RING_BUFFER_H

#include <pthread.h>
#include <stdatomic.h>

#include "common/packet.h"
#include "common/types.h"

typedef struct {
    captured_frame_t *slots;     /* capacity entries                          */
    size_t            capacity;  /* power of two                              */
    size_t            mask;      /* capacity - 1                              */

    size_t            head;      /* next write index (producer)              */
    size_t            tail;      /* next read index (consumer)               */

    pthread_mutex_t   lock;
    pthread_cond_t    not_empty;
    pthread_cond_t    not_full;

    atomic_bool       closed;    /* set on shutdown to wake blocked waiters   */
    atomic_uint_fast64_t enq_count;   /* total successfully enqueued          */
    atomic_uint_fast64_t deq_count;   /* total dequeued                       */
    atomic_uint_fast64_t drop_count;  /* dropped because full (DROP policy)   */
} ring_buffer_t;

/*
 * Allocate a ring with at least `min_capacity` slots (rounded up to a power of
 * two). Returns NPA_OK and writes *out on success.
 */
npa_result_t ring_create(ring_buffer_t **out, size_t min_capacity);

/* Free the ring and its slot storage. Safe on NULL. */
void ring_destroy(ring_buffer_t *rb);

/*
 * Copy `frame` into the ring.
 *   block == false: NPA_ERR_FULL immediately if full (caller bumps drops, or
 *                   ring_push_drop does it for you).
 *   block == true : wait on not_full until space appears or the ring closes
 *                   (NPA_ERR_AGAIN once closed).
 */
npa_result_t ring_push(ring_buffer_t *rb, const captured_frame_t *frame,
                       bool block);

/* Convenience: non-blocking push that records a drop on NPA_ERR_FULL. */
npa_result_t ring_push_drop(ring_buffer_t *rb, const captured_frame_t *frame);

/*
 * Pop the oldest frame into `out`. Blocks until an item is available or the
 * ring is closed AND drained (then NPA_ERR_AGAIN, signalling the consumer to
 * exit its loop).
 */
npa_result_t ring_pop(ring_buffer_t *rb, captured_frame_t *out);

/* Mark closed and wake all waiters so threads can exit cleanly. */
void ring_close(ring_buffer_t *rb);

/* Approximate count of queued frames (lock-free read; advisory only). */
size_t ring_size(const ring_buffer_t *rb);

/* Total frames dropped because the ring was full (DROP policy). */
u64 ring_dropped(const ring_buffer_t *rb);

/* Total capacity in slots (power of two). */
size_t ring_capacity(const ring_buffer_t *rb);

#endif /* NPA_BUFFER_RING_BUFFER_H */
