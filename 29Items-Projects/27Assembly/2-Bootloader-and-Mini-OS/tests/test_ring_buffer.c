/* =============================================================================
 *  test_ring_buffer.c  --  Host unit tests for the keyboard ring-buffer logic
 *
 *  The keyboard driver's buffer is static and IRQ-coupled, so we test the exact
 *  same algorithm here in isolation (power-of-two size + masked indices, drop
 *  on full). Keep this mirror in sync with kernel/drivers/keyboard.c.
 * ===========================================================================*/
#include "test_framework.h"

#define RB_SIZE 8                       /* power of two, like KBD_BUFFER_SIZE */

static char rb[RB_SIZE];
static unsigned rb_head, rb_tail;

static void rb_reset(void) { rb_head = rb_tail = 0; }

static int rb_push(char c) {            /* returns 1 on success, 0 if full */
    unsigned next = (rb_head + 1) & (RB_SIZE - 1);
    if (next == rb_tail) return 0;      /* full: drop */
    rb[rb_head] = c;
    rb_head = next;
    return 1;
}

static int rb_empty(void) { return rb_head == rb_tail; }

static char rb_pop(void) {              /* 0 if empty */
    if (rb_empty()) return 0;
    char c = rb[rb_tail];
    rb_tail = (rb_tail + 1) & (RB_SIZE - 1);
    return c;
}

TEST(push_then_pop_fifo_order) {
    rb_reset();
    ASSERT_TRUE(rb_push('a'));
    ASSERT_TRUE(rb_push('b'));
    ASSERT_TRUE(rb_push('c'));
    ASSERT_EQ_INT('a', rb_pop());
    ASSERT_EQ_INT('b', rb_pop());
    ASSERT_EQ_INT('c', rb_pop());
}

TEST(empty_pop_returns_zero) {
    rb_reset();
    ASSERT_TRUE(rb_empty());
    ASSERT_EQ_INT(0, rb_pop());
}

TEST(drops_on_full_without_corrupting) {
    rb_reset();
    /* Capacity is RB_SIZE-1 = 7 (one slot kept empty to distinguish full). */
    for (int i = 0; i < 7; i++) ASSERT_TRUE(rb_push((char)('0' + i)));
    ASSERT_EQ_INT(0, rb_push('X'));    /* 8th push must be rejected */
    /* Earlier data is intact and still FIFO. */
    for (int i = 0; i < 7; i++) ASSERT_EQ_INT('0' + i, rb_pop());
    ASSERT_TRUE(rb_empty());
}

TEST(index_wraps_around_repeatedly) {
    rb_reset();
    /* Push/pop far more than RB_SIZE to force the masked indices to wrap. */
    for (int i = 0; i < 100; i++) {
        ASSERT_TRUE(rb_push((char)(i & 0x7F)));
        ASSERT_EQ_INT(i & 0x7F, rb_pop());
        ASSERT_TRUE(rb_empty());
    }
}

int main(void) {
    printf("== ring buffer tests ==\n");
    RUN_TEST(push_then_pop_fifo_order);
    RUN_TEST(empty_pop_returns_zero);
    RUN_TEST(drops_on_full_without_corrupting);
    RUN_TEST(index_wraps_around_repeatedly);
    TEST_SUMMARY();
}
