/* =============================================================================
 *  test_scheduler.c  --  Host unit tests for the round-robin policy
 *
 *  Compiled together with kernel/sched/scheduler.c (see Makefile `test`
 *  target). We exercise the pure `scheduler_pick_next` function with
 *  hand-built task tables. scheduler.c references kmalloc/kfree, so we provide
 *  the two "link seam" mocks below -- nothing else is needed.
 * ===========================================================================*/
#include "test_framework.h"
#include "../kernel/include/scheduler.h"

#include <stdlib.h>

/* --- link seams: satisfy scheduler.c's only external symbols --------------- */
void *kmalloc(size_t size) { return malloc(size); }
void  kfree(void *ptr)     { free(ptr); }

/* Helper: build a task with a given state. */
static task_t mk(task_state_t s) {
    task_t t;
    t.state = s;
    t.id = 0;
    return t;
}

TEST(picks_next_in_order) {
    task_t tasks[3] = { mk(TASK_READY), mk(TASK_READY), mk(TASK_READY) };
    /* From index 0, the next runnable is 1; from 1 -> 2; from 2 wraps -> 0. */
    ASSERT_EQ_INT(1, scheduler_pick_next(tasks, 3, 0));
    ASSERT_EQ_INT(2, scheduler_pick_next(tasks, 3, 1));
    ASSERT_EQ_INT(0, scheduler_pick_next(tasks, 3, 2));
}

TEST(skips_blocked_and_terminated) {
    task_t tasks[4] = {
        mk(TASK_RUNNING), mk(TASK_BLOCKED), mk(TASK_TERMINATED), mk(TASK_READY)
    };
    /* From 0, slots 1 and 2 are not runnable, so it must land on 3. */
    ASSERT_EQ_INT(3, scheduler_pick_next(tasks, 4, 0));
}

TEST(single_runnable_task_keeps_running) {
    task_t tasks[3] = { mk(TASK_UNUSED), mk(TASK_RUNNING), mk(TASK_UNUSED) };
    /* Only index 1 is runnable; it should be chosen no matter where we start. */
    ASSERT_EQ_INT(1, scheduler_pick_next(tasks, 3, 1));
    ASSERT_EQ_INT(1, scheduler_pick_next(tasks, 3, 0));
}

TEST(no_runnable_returns_minus_one) {
    task_t tasks[2] = { mk(TASK_BLOCKED), mk(TASK_TERMINATED) };
    ASSERT_EQ_INT(-1, scheduler_pick_next(tasks, 2, 0));
}

TEST(empty_table_returns_minus_one) {
    ASSERT_EQ_INT(-1, scheduler_pick_next(NULL, 0, -1));
}

TEST(round_robin_is_fair_over_many_steps) {
    task_t tasks[3] = { mk(TASK_READY), mk(TASK_READY), mk(TASK_READY) };
    int counts[3] = {0, 0, 0};
    i32 cur = 0;
    for (int i = 0; i < 30; i++) {              /* 30 schedules over 3 tasks */
        cur = scheduler_pick_next(tasks, 3, cur);
        counts[cur]++;
    }
    /* Perfect fairness: each task selected exactly 10 times. */
    ASSERT_EQ_INT(10, counts[0]);
    ASSERT_EQ_INT(10, counts[1]);
    ASSERT_EQ_INT(10, counts[2]);
}

/* --- stateful scheduler API (drives the real internal task table) --------- */
static void dummy_entry(void) { /* never actually executed in a host test */ }

TEST(init_creates_only_the_idle_task) {
    scheduler_init();
    ASSERT_EQ_INT(1, (int)scheduler_task_count());   /* idle only */
    ASSERT_EQ_INT(0, scheduler_current_id());        /* idle id == 0 */
}

TEST(task_create_adds_ready_tasks) {
    scheduler_init();
    i32 id = task_create("worker", dummy_entry);
    ASSERT_TRUE(id > 0);
    ASSERT_EQ_INT(2, (int)scheduler_task_count());
    const task_t *t = scheduler_task(1);
    ASSERT_TRUE(t != NULL);
    ASSERT_EQ_INT(TASK_READY, t->state);
    ASSERT_STR_EQ("worker", t->name);
}

TEST(task_create_fails_when_table_full) {
    scheduler_init();
    int created = 0;
    while (task_create("t", dummy_entry) >= 0) created++;
    ASSERT_EQ_INT(MAX_TASKS - 1, created);           /* slot 0 reserved for idle */
    ASSERT_EQ_INT(-1, task_create("overflow", dummy_entry));
}

TEST(no_resched_returns_same_stack) {
    scheduler_init();
    task_create("w", dummy_entry);
    u32 esp = 0xCAFE1000;
    ASSERT_EQ_INT((int)esp, (int)scheduler_on_interrupt_return(esp));
    ASSERT_EQ_INT(0, scheduler_current_id());         /* no switch -> still idle */
}

TEST(resched_with_only_idle_is_noop) {
    scheduler_init();
    scheduler_request_resched();
    u32 esp = 0x9000;
    ASSERT_EQ_INT((int)esp, (int)scheduler_on_interrupt_return(esp));
    ASSERT_EQ_INT(0, scheduler_current_id());
}

TEST(resched_switches_and_saves_outgoing_stack) {
    scheduler_init();
    i32 w = task_create("w", dummy_entry);
    scheduler_request_resched();
    u32 idle_esp = 0xBEEF0000;
    u32 next = scheduler_on_interrupt_return(idle_esp);
    ASSERT_TRUE(next != idle_esp);                    /* switched to a new stack */
    ASSERT_EQ_INT((int)w, scheduler_current_id());    /* worker now current */
    ASSERT_EQ_INT((int)idle_esp, (int)scheduler_task(0)->ctx.esp); /* idle saved */
}

TEST(round_robin_cycles_idle_and_workers) {
    scheduler_init();
    task_create("a", dummy_entry);   /* id 1 */
    task_create("b", dummy_entry);   /* id 2 */
    int expected[6] = {1, 2, 0, 1, 2, 0};
    for (int i = 0; i < 6; i++) {
        scheduler_request_resched();
        scheduler_on_interrupt_return((u32)(0x1000 + i));
        ASSERT_EQ_INT(expected[i], scheduler_current_id());
    }
}

TEST(yield_requests_a_reschedule) {
    scheduler_init();
    i32 w = task_create("w", dummy_entry);
    task_yield();                                      /* sets need_resched */
    u32 next = scheduler_on_interrupt_return(0x2000);
    ASSERT_TRUE(next != 0x2000);
    ASSERT_EQ_INT((int)w, scheduler_current_id());
}

int main(void) {
    printf("== scheduler tests ==\n");
    RUN_TEST(picks_next_in_order);
    RUN_TEST(skips_blocked_and_terminated);
    RUN_TEST(single_runnable_task_keeps_running);
    RUN_TEST(no_runnable_returns_minus_one);
    RUN_TEST(empty_table_returns_minus_one);
    RUN_TEST(round_robin_is_fair_over_many_steps);
    RUN_TEST(init_creates_only_the_idle_task);
    RUN_TEST(task_create_adds_ready_tasks);
    RUN_TEST(task_create_fails_when_table_full);
    RUN_TEST(no_resched_returns_same_stack);
    RUN_TEST(resched_with_only_idle_is_noop);
    RUN_TEST(resched_switches_and_saves_outgoing_stack);
    RUN_TEST(round_robin_cycles_idle_and_workers);
    RUN_TEST(yield_requests_a_reschedule);
    TEST_SUMMARY();
}
