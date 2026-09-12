/*
 * scheduler.c — Priority round-robin scheduler with cooperative switching.
 *
 * One FIFO run queue per priority band; pick-next scans highest band first.
 * The ML model (ml_priority.c) advises a process's effective band on enqueue;
 * the result is clamped so a bad prediction can never push a task out of range,
 * and periodic aging in sched_tick() prevents starvation.
 *
 * Switching is cooperative: a thread calls sched_yield() (or blocks/exits) to
 * relinquish the CPU. The PIT IRQ runs concurrently and performs time
 * accounting that feeds the ML features, but does not forcibly preempt — this
 * keeps switching points well-defined and the kernel easy to reason about.
 */
#include "../include/kernel.h"
#include "../include/sched.h"
#include "../include/ml_sched.h"

/* From process.c */
pcb_t *process_create(const char *name, void (*entry)(void), uint8_t priority);
void   process_destroy(pcb_t *p);
void   process_table_init(void);

/* From context_switch.asm */
void context_switch(uint64_t *save_rsp, uint64_t new_rsp);

static pcb_t     *runq_head[SCHED_NPRIO];
static pcb_t     *runq_tail[SCHED_NPRIO];
static pcb_t     *current;
static pcb_t     *reap_pending;         /* exited thread awaiting kstack free */
static uint64_t   bootstrap_rsp;        /* scratch save slot for the first switch */
static ml_model_t ml_model;
static bool       ml_enabled = true;
static int        runnable;             /* READY + RUNNING count */

void sched_init(void)
{
    for (int i = 0; i < SCHED_NPRIO; i++)
        runq_head[i] = runq_tail[i] = NULL;
    current      = NULL;
    reap_pending = NULL;
    runnable     = 0;
    process_table_init();
    ml_model_init(&ml_model);
    KLOG_INFO("scheduler: %d priority bands, ML=%s",
              SCHED_NPRIO, ml_enabled ? "on" : "off");
}

void sched_set_ml(bool on) { ml_enabled = on; }
int  sched_runnable_count(void) { return runnable; }
ml_model_t *sched_ml_model(void) { return &ml_model; }

void sched_enqueue(pcb_t *p)
{
    if (!p) return;

    /* Each time a task is (re)queued it has waited another turn — bump its age
     * so the ML model can lift long-waiting tasks (anti-starvation). */
    if (p->feat.age_ticks < 0xFFFFFFFF) p->feat.age_ticks++;

    uint8_t band = p->base_priority;
    if (ml_enabled) {
        band = ml_priority_score(&ml_model, &p->feat);
        if (band >= SCHED_NPRIO) band = SCHED_NPRIO - 1;
    }
    p->dyn_priority = band;
    p->state        = PROC_READY;
    p->next         = NULL;

    if (runq_tail[band]) {
        runq_tail[band]->next = p;
        runq_tail[band]       = p;
    } else {
        runq_head[band] = runq_tail[band] = p;
    }
}

static pcb_t *dequeue_highest(void)
{
    for (int b = 0; b < SCHED_NPRIO; b++) {
        if (runq_head[b]) {
            pcb_t *p = runq_head[b];
            runq_head[b] = p->next;
            if (!runq_head[b]) runq_tail[b] = NULL;
            p->next = NULL;
            return p;
        }
    }
    return NULL;
}

pid_t sched_spawn(const char *name, void (*entry)(void), uint8_t priority)
{
    pcb_t *p = process_create(name, entry, priority);
    if (!p) return -ENOMEM;
    sched_enqueue(p);
    runnable++;
    return p->pid;
}

/* Switch to the next runnable thread. The caller has already placed `current`
 * (if any) into its desired state (READY+enqueued, ZOMBIE, or BLOCKED).
 *
 * The actual switch runs with interrupts disabled so the timer IRQ cannot fire
 * mid-switch; the destination re-enables them (a freshly created thread via
 * thread_trampoline, a resumed thread via the `sti` below on return). */
static void switch_to_next(void)
{
    /* Reap a thread that exited on a *previous* switch: we are now running on a
     * different stack, so freeing its kernel stack is safe. (A thread cannot
     * free the very stack it is executing on — that is the use-after-free this
     * deferral avoids.) */
    if (reap_pending) {
        process_destroy(reap_pending);
        reap_pending = NULL;
    }

    pcb_t *prev = current;
    pcb_t *next = dequeue_highest();
    if (!next) {
        /* Keep running prev if it is still runnable; otherwise nothing is left
         * to run — halt the machine cleanly. */
        if (prev && prev->state == PROC_RUNNING) return;
        kprintf("\n[sched] no runnable threads — system halted\nKERNEL: HALT\n");
        for (;;) __asm__ volatile("cli; hlt");
    }

    /* If we are switching away from an exited thread, retire it after the
     * switch (its stack is still in use until context_switch saves from it). */
    if (prev && prev->state == PROC_ZOMBIE)
        reap_pending = prev;

    next->state           = PROC_RUNNING;
    next->feat.age_ticks  = 0;          /* it just got the CPU */
    current               = next;

    uint64_t *save = prev ? &prev->context.rsp : &bootstrap_rsp;
    local_irq_disable();
    context_switch(save, next->context.rsp);
    local_irq_enable();             /* reached when THIS thread is resumed */
}

void schedule(void)
{
    switch_to_next();
}

void sched_yield(void)
{
    if (current && current->state == PROC_RUNNING) {
        current->state = PROC_READY;
        sched_enqueue(current);
    }
    switch_to_next();
}

void sched_tick(void)
{
    /* Runs in IRQ context. Touch ONLY the running thread — never the run queues
     * — so there is no data race with enqueue/dequeue in thread context. The
     * running task's CPU-burst estimate feeds the ML priority features; "age"
     * (waiting turns) is accrued race-free at enqueue time. */
    if (current)
        current->feat.cpu_burst_ema = (current->feat.cpu_burst_ema * 3 + 4) / 4;
}

pcb_t *sched_current(void) { return current; }

void sched_exit(int code)
{
    if (current) {
        KLOG_DEBUG("process pid=%d '%s' exited code=%d",
                   current->pid, current->name, code);
        /* Mark dead but keep the kernel stack alive — we are still running on
         * it. switch_to_next() arranges for it to be reaped after we switch. */
        current->state = PROC_ZOMBIE;
        runnable--;
    }
    switch_to_next();
    panic("sched_exit: scheduled past a dead process");
}
