/* =============================================================================
 *  scheduler.c  --  Preemptive round-robin scheduler
 *
 *  Context switching is done by swapping the kernel stack pointer at interrupt
 *  return time (see isr_stubs.asm + isr.c). Each task's saved esp points at a
 *  full register frame on its own stack; resuming a task is just "restore esp,
 *  pop the registers, iret". A brand-new task gets a hand-crafted frame so its
 *  first resume `iret`s straight into its entry point.
 *
 *  Slot 0 is the idle task -- the boot/kernel context itself, captured lazily on
 *  the first switch. That guarantees there is always something runnable.
 *
 *  The pure policy (scheduler_pick_next) is host-unit-tested; the link surface
 *  is just kmalloc/kfree so the test needs only those two mocks.
 * ===========================================================================*/
#include "../include/scheduler.h"
#include "../include/memory.h"
#include "../include/cpu.h"

#define KERNEL_CODE_SEL 0x08    /* CODE_SEG from boot/gdt.asm */
#define KERNEL_DATA_SEL 0x10    /* DATA_SEG from boot/gdt.asm */
#define INITIAL_EFLAGS  0x202   /* reserved bit 1 + IF (interrupts enabled) */

static task_t tasks[MAX_TASKS];
static i32    current = 0;          /* index of the running task (0 = idle) */
static u32    task_count = 0;
static u32    next_id = 1;
static volatile bool need_resched = false;

void scheduler_init(void) {
    for (u32 i = 0; i < MAX_TASKS; i++) {
        tasks[i].state = TASK_UNUSED;
        tasks[i].stack_base = NULL;
        tasks[i].ticks_run = 0;
    }

    /* Slot 0 = idle task: represents the current kernel/boot context. Its saved
     * esp is filled in on the first context switch (when we know it). */
    tasks[0].id = 0;
    tasks[0].state = TASK_RUNNING;
    tasks[0].stack_base = NULL;
    tasks[0].ctx.esp = 0;
    {
        const char *n = "idle";
        u32 i = 0;
        for (; n[i] && i < TASK_NAME_LEN - 1; i++) tasks[0].name[i] = n[i];
        tasks[0].name[i] = '\0';
    }

    current = 0;
    task_count = 1;
    next_id = 1;
    need_resched = false;
}

static void copy_name(char *dst, const char *src) {
    u32 i = 0;
    for (; src[i] && i < TASK_NAME_LEN - 1; i++) dst[i] = src[i];
    dst[i] = '\0';
}

/* Build the initial interrupt-style frame on a task's stack so that the first
 * resume restores zeroed registers and `iret`s into entry() at ring 0 with
 * interrupts enabled. Layout MUST match registers_t + the stub's pop sequence:
 *   [ds][edi esi ebp esp ebx edx ecx eax][int_no err_code][eip cs eflags][ret] */
static u32 build_initial_frame(u8 *stack_top, task_entry_t entry) {
    u32 *sp = (u32 *)(void *)stack_top;
    *(--sp) = (u32)(uintptr_t)task_exit;  /* return addr if entry() ever returns */
    *(--sp) = INITIAL_EFLAGS;             /* eflags  (IF set) */
    *(--sp) = KERNEL_CODE_SEL;            /* cs */
    *(--sp) = (u32)(uintptr_t)entry;      /* eip -> task entry */
    *(--sp) = 0;                          /* err_code (skipped by add esp,8) */
    *(--sp) = 0;                          /* int_no  (skipped) */
    *(--sp) = 0;                          /* eax */
    *(--sp) = 0;                          /* ecx */
    *(--sp) = 0;                          /* edx */
    *(--sp) = 0;                          /* ebx */
    *(--sp) = 0;                          /* esp (ignored by popa) */
    *(--sp) = 0;                          /* ebp */
    *(--sp) = 0;                          /* esi */
    *(--sp) = 0;                          /* edi */
    *(--sp) = KERNEL_DATA_SEL;            /* ds */
    return (u32)(uintptr_t)sp;            /* saved esp points at the ds slot */
}

i32 task_create(const char *name, task_entry_t entry) {
    for (u32 i = 1; i < MAX_TASKS; i++) {   /* slot 0 reserved for idle */
        if (tasks[i].state != TASK_UNUSED) continue;

        u8 *stack = (u8 *)kmalloc(TASK_STACK_SIZE);
        if (!stack) return -1;              /* heap exhausted: recoverable */

        task_t *t = &tasks[i];
        t->id    = next_id++;
        t->state = TASK_READY;
        t->stack_base = stack;
        t->ticks_run  = 0;
        copy_name(t->name, name);
        t->ctx.esp = build_initial_frame(stack + TASK_STACK_SIZE, entry);

        task_count++;
        return (i32)t->id;
    }
    return -1;                              /* task table full */
}

/* -------- PURE round-robin policy (host-unit-tested, no side effects) ------ */
i32 scheduler_pick_next(const task_t *tasks_arr, u32 count, i32 cur) {
    if (count == 0) return -1;
    for (u32 step = 1; step <= count; step++) {
        i32 idx = (i32)(((u32)(cur + (i32)step)) % count);
        task_state_t s = tasks_arr[idx].state;
        if (s == TASK_READY || s == TASK_RUNNING) return idx;
    }
    return -1;
}

void scheduler_request_resched(void) {
    need_resched = true;
}

/* Called at the tail of every IRQ. If a reschedule was requested, save the
 * current task's stack pointer and return the next task's -- the assembly stub
 * then resumes that task. Otherwise return the same esp (no switch). */
u32 scheduler_on_interrupt_return(u32 cur_esp) {
    if (!need_resched) return cur_esp;
    need_resched = false;

    if (task_count <= 1) return cur_esp;    /* only idle exists: nothing to do */

    /* Save outgoing task. */
    tasks[current].ctx.esp = cur_esp;
    if (tasks[current].state == TASK_RUNNING) tasks[current].state = TASK_READY;

    i32 next = scheduler_pick_next(tasks, MAX_TASKS, current);
    if (next < 0) {                         /* nothing runnable: keep running */
        tasks[current].state = TASK_RUNNING;
        return cur_esp;
    }

    tasks[next].state = TASK_RUNNING;
    tasks[next].ticks_run++;
    current = next;
    return tasks[next].ctx.esp;             /* resume the next task */
}

/* Cooperative yield: halt until the next timer tick, which will preempt us.
 * Cheap and correct -- no separate yield vector needed. */
void task_yield(void) {
    scheduler_request_resched();
    cpu_halt();                             /* sleep until the next timer tick */
}

/* Terminate the current task. Marks the slot reclaimable, frees its stack, then
 * forces a reschedule by halting; we never come back. */
void task_exit(void) {
    if (current > 0) {                      /* never terminate the idle task */
        tasks[current].state = TASK_TERMINATED;
        if (tasks[current].stack_base) {
            kfree(tasks[current].stack_base);
            tasks[current].stack_base = NULL;
        }
        if (task_count) task_count--;
    }
    scheduler_request_resched();
    for (;;) cpu_halt();                     /* wait to be switched away forever */
}

/* ----------------------------- introspection ------------------------------ */
u32 scheduler_task_count(void) { return task_count; }

const task_t *scheduler_task(u32 index) {
    if (index >= MAX_TASKS) return NULL;
    return &tasks[index];
}

i32 scheduler_current_id(void) {
    return (current >= 0) ? (i32)tasks[current].id : -1;
}
