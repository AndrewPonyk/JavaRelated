/*
 * test_scheduler.c — Host unit tests for the scheduler core.
 *
 * scheduler.c depends on the context switch (asm) and the process allocator
 * (process.c). For host testing we supply lightweight stubs for both — the host
 * context_switch is a no-op that simply returns, so schedule() returns to the
 * test and we can assert on the resulting bookkeeping without real stacks.
 *
 * Compiled with -DKERNEL_HOSTTEST so scheduler.c's cli/sti become no-ops.
 * Links: scheduler.c + ml_priority.c + this file.
 */
#include "test_framework.h"
#include "../kernel/include/sched.h"

/* ---- Host stubs for kernel-only dependencies ---- */
int  kprintf(const char *fmt, ...) { (void)fmt; return 0; }
void klog(int level, const char *fmt, ...) { (void)level; (void)fmt; }
void panic(const char *fmt, ...) { (void)fmt; for (;;) { } }

/* No-op context switch: on the host we only verify scheduler bookkeeping. */
void context_switch(uint64_t *save_rsp, uint64_t new_rsp)
{
    (void)save_rsp; (void)new_rsp;
}

/* Static PCB pool standing in for process.c's allocator. */
static pcb_t pool[MAX_PROCESSES];
static int   pool_next;

void process_table_init(void) { pool_next = 0; }
void process_destroy(pcb_t *p) { if (p) p->state = PROC_UNUSED; }

pcb_t *process_create(const char *name, void (*entry)(void), uint8_t priority)
{
    if (pool_next >= MAX_PROCESSES) return NULL;
    pcb_t *p = &pool[pool_next++];
    p->pid              = pool_next;          /* 1-based */
    p->state            = PROC_READY;
    p->base_priority    = priority;
    p->dyn_priority     = priority;
    p->aspace           = NULL;
    p->next             = NULL;
    p->context.rsp      = 0;                  /* host switch ignores it */
    p->feat.cpu_burst_ema = 0;
    p->feat.io_wait_ema   = 0;
    p->feat.age_ticks     = 0;
    p->feat.nice          = 0;
    (void)name; (void)entry;
    return p;
}

static void dummy_entry(void) { /* never actually run on host */ }

static void reset(void)
{
    sched_init();
    sched_set_ml(true);          /* restore default each test */
}

TEST(spawn_returns_positive_pid)
{
    reset();
    pid_t pid = sched_spawn("a", dummy_entry, 2);
    ASSERT_TRUE(pid > 0);
}

TEST(schedule_selects_a_process)
{
    reset();
    sched_spawn("a", dummy_entry, 3);
    ASSERT_NULL(sched_current());        /* nothing running yet */
    schedule();
    ASSERT_NOT_NULL(sched_current());    /* one now running */
    ASSERT_EQ(sched_current()->state, PROC_RUNNING);
}

TEST(higher_priority_runs_first)
{
    reset();
    sched_set_ml(false);                 /* use static base priorities */
    pid_t lo = sched_spawn("low",  dummy_entry, 5);
    pid_t hi = sched_spawn("high", dummy_entry, 0);   /* band 0 = highest */
    (void)lo;
    schedule();
    ASSERT_NOT_NULL(sched_current());
    ASSERT_EQ(sched_current()->pid, hi); /* highest-priority task picked first */
}

TEST(runnable_count_tracks_spawns)
{
    reset();
    ASSERT_EQ(sched_runnable_count(), 0);
    sched_spawn("a", dummy_entry, 2);
    sched_spawn("b", dummy_entry, 2);
    ASSERT_EQ(sched_runnable_count(), 2);
}

int main(void)
{
    printf("scheduler tests:\n");
    RUN(spawn_returns_positive_pid);
    RUN(schedule_selects_a_process);
    RUN(higher_priority_runs_first);
    RUN(runnable_count_tracks_spawns);
    return test_summary();
}
