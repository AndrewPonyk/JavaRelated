/* =============================================================================
 *  kernel.c  --  Mini-OS kernel entry point
 *
 *  kernel_main() is called by kernel_entry.asm once the bootloader has switched
 *  the CPU into 32-bit protected mode. It brings up each subsystem bottom-up
 *  (the layered architecture from ARCHITECTURE.md), spawns the shell plus two
 *  demo tasks, enables interrupts, and becomes the idle task.
 * ===========================================================================*/
#include "include/types.h"
#include "include/gdt.h"
#include "include/vga.h"
#include "include/serial.h"
#include "include/kprintf.h"
#include "include/isr.h"
#include "include/pic.h"
#include "include/timer.h"
#include "include/keyboard.h"
#include "include/paging.h"
#include "include/pmm.h"
#include "include/memory.h"
#include "include/scheduler.h"
#include "include/shell.h"

/* Physical RAM available to the PMM. Matches the QEMU `-m` default (64 MiB) in
 * .env / docker-compose; raise it to match a larger guest. */
#define PHYS_MEM_KB (64u * 1024u)

static bool serial_ok = false;

/* The character sink kprintf streams through: VGA always, serial if present. */
static void console_putchar(char c) {
    vga_putchar(c);
    if (serial_ok) serial_putchar(c);
}

static void log_ok(const char *msg) {
    vga_set_color(VGA_LIGHT_GREEN, VGA_BLACK);
    kprintf("[ OK ] ");
    vga_set_color(VGA_LIGHT_GREY, VGA_BLACK);
    kprintf("%s\n", msg);
}

/* ---- demo tasks: two spinners that visibly time-slice (Phase-2 acceptance) -- */
static void task_a(void) {
    static const char spin[] = "|/-\\";
    u32 n = 0;
    for (;;) {
        vga_putc_at('A', 71, 0, vga_attr(VGA_LIGHT_CYAN, VGA_BLACK));
        vga_putc_at(spin[n & 3], 72, 0, vga_attr(VGA_LIGHT_CYAN, VGA_BLACK));
        n++;
        task_yield();
    }
}

static void task_b(void) {
    static const char spin[] = "|/-\\";
    u32 n = 0;
    for (;;) {
        vga_putc_at('B', 75, 0, vga_attr(VGA_YELLOW, VGA_BLACK));
        vga_putc_at(spin[n & 3], 76, 0, vga_attr(VGA_YELLOW, VGA_BLACK));
        n++;
        task_yield();
    }
}

void kernel_main(void) {
    /* Install our OWN GDT first: the bootloader's lives in the boot sector at
     * ~0x7C00, which this kernel's BSS overgrows and corrupts. Without this, the
     * first hardware interrupt triple-faults reloading CS from a dead GDT. */
    gdt_init();

    /* Output next, so every later step is observable on screen and serial. */
    vga_init();
    serial_ok = (serial_init() == 0);
    kprintf_set_sink(console_putchar);

    vga_set_color(VGA_WHITE, VGA_BLUE);
    kprintf(" Mini-OS  --  x86 educational kernel  (v0.3) \n");
    vga_set_color(VGA_LIGHT_GREY, VGA_BLACK);
    kprintf("Mini-OS booting...\n\n");           /* CI asserts on this banner */

    /* Layer 1: CPU interrupt plumbing. ORDER MATTERS (see TECH-NOTES):
     * remap PIC -> install IDT/ISRs -> ... -> unmask -> sti (done last). */
    pic_remap();          log_ok("PIC remapped to 0x20-0x2F");
    isr_install();        log_ok("IDT + ISR/IRQ handlers installed");

    /* Memory: physical frame allocator, then paging, then the kernel heap. */
    pmm_init(PHYS_MEM_KB);
    pmm_reserve_region(0x00000000, 0x00100000);  /* low 1 MiB: IVT/boot/kernel */
    pmm_reserve_region(KHEAP_START, KHEAP_SIZE);  /* the fixed kernel heap */
    log_ok("Physical memory manager online");

    paging_init();        log_ok("Paging enabled (identity map, low 4 MiB)");
    heap_init();          log_ok("Kernel heap online");

    scheduler_init();     log_ok("Round-robin scheduler ready");

    /* Layer 2 drivers that depend on interrupts. */
    timer_init(100);      log_ok("PIT timer @ 100 Hz");
    keyboard_init();      log_ok("PS/2 keyboard driver");

    /* Unmask the lines we actually use (don't rely on BIOS leaving them open). */
    pic_clear_mask(0);    /* timer  */
    pic_clear_mask(1);    /* keyboard */

    /* Layer 4: spawn the workload. task_create returns -1 if the task table is
     * full or the heap can't back a new stack -- surface that instead of
     * silently booting with a missing shell. */
    i32 shell_id = task_create("shell",  shell_run);
    i32 a_id     = task_create("task_a", task_a);
    i32 b_id     = task_create("task_b", task_b);
    if (shell_id < 0 || a_id < 0 || b_id < 0) {
        vga_set_color(VGA_LIGHT_RED, VGA_BLACK);
        kprintf("[!!] WARNING: a task failed to spawn (shell=%d a=%d b=%d)\n",
                shell_id, a_id, b_id);
        vga_set_color(VGA_LIGHT_GREY, VGA_BLACK);
    } else {
        log_ok("Spawned shell + demo tasks A and B");
    }

    kprintf("\nReady. Interrupts on; scheduler driving %u tasks.\n",
            scheduler_task_count());

    /* Go live: the PIT now preempts into the scheduler and the keyboard fills
     * the input buffer. From here kernel_main IS the idle task (slot 0). */
    __asm__ volatile ("sti");

    for (;;) __asm__ volatile ("hlt");   /* idle: sleep until the next interrupt */
}
