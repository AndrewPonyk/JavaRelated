/* =============================================================================
 *  scheduler.h  --  Preemptive round-robin task scheduler
 *
 *  Tasks are kernel threads (ring 0) sharing one address space. Slot 0 is the
 *  implicit "idle" task: the boot/kernel context that becomes schedulable on the
 *  first timer tick. Preemption is driven by the PIT (IRQ0): the timer requests
 *  a reschedule, and the interrupt-return path swaps to the next task's saved
 *  kernel stack -- see scheduler_on_interrupt_return() and isr_stubs.asm.
 *
 *  The *pure ordering logic* (scheduler_pick_next) has no side effects and is
 *  unit-tested on the host -- see tests/test_scheduler.c.
 * ===========================================================================*/
#ifndef MINIOS_SCHEDULER_H
#define MINIOS_SCHEDULER_H

#include "types.h"

#define MAX_TASKS       8       /* including the idle task in slot 0 */
#define TASK_STACK_SIZE 4096
#define TASK_NAME_LEN   16

typedef enum {
    TASK_UNUSED = 0,            /* slot is free */
    TASK_READY,                 /* runnable */
    TASK_RUNNING,               /* currently on the CPU */
    TASK_BLOCKED,               /* waiting (e.g. on I/O) -- skipped by scheduler */
    TASK_TERMINATED             /* finished; slot reclaimable */
} task_state_t;

typedef void (*task_entry_t)(void);

/* Saved context for a task. With the interrupt-frame switching model, the only
 * thing we truly need to save is the kernel stack pointer -- it points at a full
 * register frame on the task's own stack. */
typedef struct {
    u32 esp;                    /* saved kernel stack pointer (-> register frame) */
} cpu_context_t;

typedef struct {
    u32          id;
    char         name[TASK_NAME_LEN];
    task_state_t state;
    cpu_context_t ctx;
    u8          *stack_base;    /* heap-allocated stack (NULL for idle) */
    u32          ticks_run;     /* scheduling fairness / bookkeeping metric */
} task_t;

void scheduler_init(void);      /* installs the idle task in slot 0 */

/* Create a READY task with its own stack and a pre-built first-run frame.
 * Returns the task id, or -1 if the table is full / out of memory. */
i32  task_create(const char *name, task_entry_t entry);

void task_yield(void);          /* cooperatively wait for the next preemption */
void task_exit(void);           /* terminate the current task (never returns) */

/* --- interrupt-context entry points (called from irq_handler/timer) --- */
void scheduler_request_resched(void);              /* timer asks to preempt */
u32  scheduler_on_interrupt_return(u32 cur_esp);   /* maybe switch; returns esp */

/* Introspection for the shell / diagnostics. */
u32  scheduler_task_count(void);
const task_t *scheduler_task(u32 index);
i32  scheduler_current_id(void);

/* --- pure logic, exposed for host unit testing --- */
/* Given the current index and the task table, return the index of the next
 * task to run (round-robin over READY/RUNNING slots), or -1 if none. */
i32  scheduler_pick_next(const task_t *tasks, u32 count, i32 current);

#endif /* MINIOS_SCHEDULER_H */
