/*
 * kmain.c — C entry point of the kernel.
 *
 * Control arrives from boot.asm (long mode, higher half, kernel stack set up)
 * with the Multiboot2 info pointer in arg0. We bring up subsystems in
 * dependency order, then start the scheduler with an init thread (which runs
 * the self-tests and subsystem demo) and an idle thread.
 */
#include "include/kernel.h"
#include "include/memory.h"
#include "include/sched.h"
#include "include/syscall.h"
#include "include/vfs.h"
#include "include/ml_sched.h"
#include "include/multiboot.h"

/* arch / drivers */
void gdt_init(void);
void idt_init(void);
void pic_init(void);
void pit_init(uint32_t hz);
void serial_init(void);
void vga_clear(void);
void keyboard_init(void);

/* features */
int  selftest_run(void);
void kshell_run_script(const char *const *cmds, int n);
void kshell_interactive(void);

/* 1 MiB kernel heap, statically reserved in .bss (mapped via the higher half). */
static uint8_t kernel_heap[1024 * 1024] __attribute__((aligned(16)));

/* Cleanly power off QEMU when the `isa-debug-exit` device is present (CI adds
 * `-device isa-debug-exit,iobase=0xf4,iosize=0x04`). QEMU exits with status
 * (value << 1) | 1, so 0x10 -> 33. A no-op on real hardware / plain `make run`,
 * where port 0xf4 is simply ignored. */
#define QEMU_EXIT_PORT    0xf4
#define QEMU_EXIT_SUCCESS 0x10          /* -> host exit code 33 */

static void qemu_exit(uint8_t value)
{
    __asm__ volatile("outl %0, %1"
                     :: "a"((uint32_t)value), "Nd"((uint16_t)QEMU_EXIT_PORT));
}

/* ------------------------------------------------------------------ */
/* Demo threads                                                        */
/* ------------------------------------------------------------------ */

/* Worker: prints a few interleaved iterations then exits. Demonstrates context
 * switching and round-robin scheduling. All three share this entry; identity
 * comes from the PCB name. */
static void worker_thread(void)
{
    pcb_t *me = sched_current();
    for (int i = 0; i < 3; i++) {
        kprintf("    [%s] iteration %d/3 (band %u)\n",
                me->name, i + 1, me->dyn_priority);
        sched_yield();
    }
    kprintf("    [%s] done\n", me->name);
}

static void demo_ml_model(void)
{
    ml_model_t *m = sched_ml_model();
    kprintf("\n-- ML priority model: band for different process profiles --\n");
    struct { const char *label; sched_features_t f; } cases[] = {
        { "cpu-bound  ", { .cpu_burst_ema = 400, .io_wait_ema = 0,   .age_ticks = 0,   .nice = 0 } },
        { "io-bound   ", { .cpu_burst_ema = 0,   .io_wait_ema = 400, .age_ticks = 0,   .nice = 0 } },
        { "starved    ", { .cpu_burst_ema = 100, .io_wait_ema = 0,   .age_ticks = 500, .nice = 0 } },
        { "niced down ", { .cpu_burst_ema = 50,  .io_wait_ema = 50,  .age_ticks = 0,   .nice = 15 } },
    };
    for (unsigned i = 0; i < sizeof(cases) / sizeof(cases[0]); i++)
        kprintf("   %s -> band %u\n", cases[i].label,
                ml_priority_score(m, &cases[i].f));
    kprintf("   (lower band = higher priority)\n");
}

static void demo_shell(void)
{
    static const char *script[] = {
        "help",
        "write hello.txt Hello from ramfs!",
        "write todo.txt finish the kernel",
        "ls",
        "cat hello.txt",
        "meminfo",
        "ps",
        "uptime",
    };
    kprintf("\n-- shell demo (scripted) --\n");
    kshell_run_script(script, sizeof(script) / sizeof(script[0]));
}

/* Init thread: runs self-tests, spawns workers, demonstrates the ML model and
 * the shell, then exits. */
static void init_thread(void)
{
    kprintf("\n[init] pid=%d starting\n", sched_current()->pid);

    int failed = selftest_run();

    kprintf("-- scheduler demo: spawning 3 worker threads --\n");
    sched_spawn("worker-A", worker_thread, 2);
    sched_spawn("worker-B", worker_thread, 2);
    sched_spawn("worker-C", worker_thread, 2);

    /* Cooperatively yield until the workers have finished (only init + idle
     * remain runnable). */
    while (sched_runnable_count() > 2)
        sched_yield();

    demo_ml_model();
    demo_shell();

    kprintf("\n[init] self-tests %s\n",
            failed == 0 ? "ALL PASSED" : "had FAILURES");
    kprintf("DEMO COMPLETE\n");

#ifdef CONFIG_INTERACTIVE
    /* Hand off to the live shell (built via `make run-shell`). Never returns;
     * type commands in the QEMU window. */
    kshell_interactive();
#endif
    /* Default build: returning hands control to thread_trampoline -> sched_exit,
     * leaving only idle, which then halts the machine. */
}

/* Idle thread: the lowest-priority fallback. Yields while others are runnable;
 * once it is the only thread left, announces halt and stops the CPU. */
static void idle_thread(void)
{
    for (;;) {
        if (sched_runnable_count() <= 1) {
            kprintf("\n[idle] no runnable threads — system halted\n");
            kprintf("KERNEL: HALT\n");
            qemu_exit(QEMU_EXIT_SUCCESS);   /* exits QEMU under CI; else no-op */
            for (;;) __asm__ volatile("hlt");
        }
        sched_yield();
    }
}

/* ------------------------------------------------------------------ */
/* Boot                                                                */
/* ------------------------------------------------------------------ */

__attribute__((noreturn))
void kernel_main(uint64_t mb_info)
{
    serial_init();
    vga_clear();
    KLOG_INFO("microkernel v%s booting (mb_info=%x)",
              KERNEL_VERSION, (unsigned long)mb_info);

    /* 1. Architecture: descriptors, interrupt vectors, PIC, timer, keyboard. */
    gdt_init();
    idt_init();
    pic_init();
    pit_init(100);          /* 100 Hz */
    keyboard_init();

    /* 2. Memory: size RAM from the Multiboot2 map, then PMM -> paging -> heap. */
    uintptr_t base; size_t len;
    if (mb_parse_memory(mb_info, &base, &len)) {
        /* Keep a healthy margin above the loaded kernel image. */
        if (base < 0x1000000UL) {
            len -= (0x1000000UL - base);
            base = 0x1000000UL;     /* start frames at 16 MiB */
        }
    } else {
        KLOG_WARN("no multiboot mmap; assuming 16..80 MiB usable");
        base = 0x1000000UL;
        len  = 0x4000000UL;         /* 64 MiB */
    }
    pmm_init(base, len);
    vmm_init();
    kheap_init((uintptr_t)kernel_heap, sizeof(kernel_heap));
    KLOG_INFO("memory: %u frames free, %u KiB heap",
              (unsigned long)pmm_free_count(),
              (unsigned long)(sizeof(kernel_heap) >> 10));

    /* 3. Services + syscall boundary. */
    vfs_init();
    vfs_mount_ramfs();
    syscall_init();

    /* 4. Scheduler: init + idle threads, then enable interrupts and run. */
    sched_init();
    sched_spawn("init", init_thread, 0);
    sched_spawn("idle", idle_thread, SCHED_NPRIO - 1);

    KLOG_INFO("boot complete — starting scheduler");
    __asm__ volatile("sti");        /* let the timer tick */
    schedule();                     /* never returns to here */

    panic("schedule() returned with no runnable threads");
}
