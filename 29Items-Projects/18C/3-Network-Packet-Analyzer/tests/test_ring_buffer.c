/* SPDX-License-Identifier: MIT
 *
 * tests/test_ring_buffer.c — correctness + concurrency tests for the ring.
 *
 * The ring is the concurrency heart of the pipeline, so it gets the most
 * scrutiny: capacity rounding, FIFO order, full/empty edges, drop accounting,
 * and a 1-producer/1-consumer stress test asserting no loss and no dupes.
 */
#include "test_common.h"

#include "buffer/ring_buffer.h"

#include <pthread.h>

/* Build a frame whose first 4 bytes encode `seq` so we can verify ordering. */
static captured_frame_t make_frame(u32 seq) {
    captured_frame_t f = {0};
    f.caplen = 4;
    f.data[0] = (u8)(seq >> 24); f.data[1] = (u8)(seq >> 16);
    f.data[2] = (u8)(seq >> 8);  f.data[3] = (u8)seq;
    return f;
}
static u32 frame_seq(const captured_frame_t *f) {
    return (u32)f->data[0] << 24 | (u32)f->data[1] << 16 |
           (u32)f->data[2] << 8  | (u32)f->data[3];
}

static void test_capacity_rounds_to_pow2(void) {
    ring_buffer_t *rb = NULL;
    ASSERT_EQ_INT(NPA_OK, ring_create(&rb, 5));
    ASSERT_EQ_UINT(8u, rb->capacity);     /* 5 → 8 */
    ring_destroy(rb);
}

static void test_fifo_order(void) {
    ring_buffer_t *rb = NULL;
    ring_create(&rb, 8);
    for (u32 i = 0; i < 5; ++i) {
        captured_frame_t f = make_frame(i);
        ASSERT_EQ_INT(NPA_OK, ring_push(rb, &f, false));
    }
    for (u32 i = 0; i < 5; ++i) {
        captured_frame_t out;
        /* Ring is non-empty and not closed, so ring_pop won't block here. */
        ASSERT_EQ_INT(NPA_OK, ring_pop(rb, &out));
        ASSERT_EQ_UINT(i, frame_seq(&out));
    }
    ring_destroy(rb);
}

static void test_full_returns_err(void) {
    ring_buffer_t *rb = NULL;
    ring_create(&rb, 2);                   /* capacity 2 */
    captured_frame_t f = make_frame(1);
    ASSERT_EQ_INT(NPA_OK,        ring_push(rb, &f, false));
    ASSERT_EQ_INT(NPA_OK,        ring_push(rb, &f, false));
    ASSERT_EQ_INT(NPA_ERR_FULL,  ring_push(rb, &f, false));
    ASSERT_EQ_INT(NPA_ERR_FULL,  ring_push_drop(rb, &f));
    ASSERT_EQ_UINT(1u, rb->drop_count);
    ring_destroy(rb);
}

/* --- concurrency stress: one producer, one consumer, N items --- */
#define STRESS_N 100000u   /* unsigned: keeps loop comparisons sign-clean */

typedef struct { ring_buffer_t *rb; } stress_arg_t;

static void *producer(void *arg) {
    ring_buffer_t *rb = ((stress_arg_t *)arg)->rb;
    for (u32 i = 0; i < STRESS_N; ++i) {
        captured_frame_t f = make_frame(i);
        while (ring_push(rb, &f, true) != NPA_OK) { /* retry on close race */ }
    }
    ring_close(rb);
    return NULL;
}

static void test_spsc_stress_no_loss(void) {
    ring_buffer_t *rb = NULL;
    ring_create(&rb, 1024);
    stress_arg_t arg = { rb };
    pthread_t prod;
    pthread_create(&prod, NULL, producer, &arg);

    u32 expected = 0;
    captured_frame_t out;
    for (;;) {
        npa_result_t r = ring_pop(rb, &out);
        if (r == NPA_ERR_AGAIN) break;     /* closed + drained */
        if (r != NPA_OK) continue;
        ASSERT_EQ_UINT(expected, frame_seq(&out));  /* strict FIFO, no gaps */
        expected++;
    }
    pthread_join(prod, NULL);
    ASSERT_EQ_UINT((unsigned)STRESS_N, expected);   /* every item arrived once */
    ring_destroy(rb);
}

int main(void) {
    printf("ring_buffer tests:\n");
    RUN_TEST(test_capacity_rounds_to_pow2);
    RUN_TEST(test_fifo_order);
    RUN_TEST(test_full_returns_err);
    RUN_TEST(test_spsc_stress_no_loss);
    return test_summary("ring_buffer");
}
