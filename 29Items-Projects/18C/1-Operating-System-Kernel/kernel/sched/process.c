/*
 * process.c — Process Control Block lifecycle and the process table.
 *
 * A new kernel thread's stack is "primed" so the very first context switch into
 * it pops six zeroed callee-saved registers (with the entry point parked in
 * r15) and `ret`s into thread_trampoline (context_switch.asm), which enables
 * interrupts and calls the entry function.
 */
#include "../include/kernel.h"
#include "../include/memory.h"
#include "../include/sched.h"

/* From context_switch.asm */
extern void thread_trampoline(void);

#define KSTACK_SIZE  (16 * 1024)

static pcb_t proc_table[MAX_PROCESSES];
static pid_t next_pid = 1;

void process_table_init(void)
{
    for (int i = 0; i < MAX_PROCESSES; i++) {
        proc_table[i].state = PROC_UNUSED;
        proc_table[i].next  = NULL;
    }
    next_pid = 1;
}

pcb_t *process_table(void) { return proc_table; }

static pcb_t *alloc_pcb(void)
{
    for (int i = 0; i < MAX_PROCESSES; i++)
        if (proc_table[i].state == PROC_UNUSED)
            return &proc_table[i];
    return NULL;
}

static void str_copy(char *dst, const char *src, size_t max)
{
    size_t i = 0;
    for (; src[i] && i < max - 1; i++) dst[i] = src[i];
    dst[i] = '\0';
}

pcb_t *process_create(const char *name, void (*entry)(void), uint8_t priority)
{
    pcb_t *p = alloc_pcb();
    if (!p) {
        KLOG_ERROR("process: table full");
        return NULL;
    }

    p->pid           = next_pid++;
    str_copy(p->name, name, PROC_NAME_LEN);
    p->state         = PROC_READY;
    p->base_priority = priority < SCHED_NPRIO ? priority : SCHED_NPRIO - 1;
    p->dyn_priority  = p->base_priority;
    p->aspace        = NULL;               /* kernel thread: shares kernel space */
    p->next          = NULL;

    p->kstack = kmalloc(KSTACK_SIZE);
    if (!p->kstack) {
        p->state = PROC_UNUSED;
        return NULL;
    }

    /* Prime the stack. Layout the first context switch will pop, low->high:
     *   r15(=entry) r14 r13 r12 rbp rbx  ret->thread_trampoline             */
    uint64_t *sp = (uint64_t *)((uintptr_t)p->kstack + KSTACK_SIZE);
    *(--sp) = (uint64_t)thread_trampoline;  /* ret target                    */
    *(--sp) = 0;                            /* rbx                           */
    *(--sp) = 0;                            /* rbp                           */
    *(--sp) = 0;                            /* r12                           */
    *(--sp) = 0;                            /* r13                           */
    *(--sp) = 0;                            /* r14                           */
    *(--sp) = (uint64_t)entry;              /* r15 -> entry for trampoline   */
    p->context.rsp = (uint64_t)sp;

    p->feat.cpu_burst_ema = 0;
    p->feat.io_wait_ema   = 0;
    p->feat.age_ticks     = 0;
    p->feat.nice          = 0;

    KLOG_DEBUG("process: created '%s' pid=%d prio=%u", p->name, p->pid, priority);
    return p;
}

void process_destroy(pcb_t *p)
{
    if (!p) return;
    if (p->kstack) kfree(p->kstack);
    p->state  = PROC_UNUSED;
    p->kstack = NULL;
}
