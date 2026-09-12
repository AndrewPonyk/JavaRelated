/*
 * sched.h — Process model and scheduler interface.
 */
#ifndef KERNEL_SCHED_H
#define KERNEL_SCHED_H

#include "types.h"

#define MAX_PROCESSES   64
#define PROC_NAME_LEN   32
#define SCHED_NPRIO     8   /* priority bands 0 (highest) .. 7 (lowest) */

typedef enum {
    PROC_UNUSED = 0,
    PROC_READY,
    PROC_RUNNING,
    PROC_BLOCKED,
    PROC_ZOMBIE,
} proc_state_t;

/* Saved execution context for a kernel thread. Callee-saved registers live on
 * the thread's own stack, so the context is just its saved stack pointer
 * (see context_switch.asm). */
typedef struct cpu_context {
    uint64_t rsp;
} cpu_context_t;

/* Features the ML priority model observes — kept here so the scheduler can
 * accumulate them cheaply on every tick / context switch. */
typedef struct sched_features {
    uint32_t cpu_burst_ema;  /* exponential moving avg of CPU burst (ticks) */
    uint32_t io_wait_ema;    /* EMA of time spent blocked on I/O */
    uint32_t age_ticks;      /* ticks since last scheduled (anti-starvation) */
    int32_t  nice;           /* user hint, -20..19 */
} sched_features_t;

/* Process Control Block. */
typedef struct pcb {
    pid_t            pid;
    char             name[PROC_NAME_LEN];
    proc_state_t     state;
    uint8_t          base_priority;   /* static class */
    uint8_t          dyn_priority;    /* effective band, possibly ML-adjusted */
    cpu_context_t    context;
    void            *kstack;          /* kernel stack top */
    struct address_space *aspace;     /* NULL for kernel threads */
    sched_features_t feat;
    struct pcb      *next;            /* run-queue link */
} pcb_t;

struct ml_model;                /* defined in ml_sched.h */

/* ---- Scheduler API ---------------------------------------------- */
void   sched_init(void);
pid_t  sched_spawn(const char *name, void (*entry)(void), uint8_t priority);
void   sched_enqueue(pcb_t *p);
void   schedule(void);          /* pick next & context switch */
void   sched_yield(void);       /* voluntary reschedule */
void   sched_tick(void);        /* called from timer IRQ (accounting only) */
pcb_t *sched_current(void);
void   sched_exit(int code);
void   sched_set_ml(bool on);
int    sched_runnable_count(void);
struct ml_model *sched_ml_model(void);

/* ---- Process table (process.c) ---------------------------------- */
void   process_table_init(void);
pcb_t *process_table(void);

#endif /* KERNEL_SCHED_H */
