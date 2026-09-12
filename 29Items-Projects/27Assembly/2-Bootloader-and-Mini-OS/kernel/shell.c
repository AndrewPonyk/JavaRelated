/* =============================================================================
 *  shell.c  --  Interactive command shell (runs as a task)
 *
 *  Commands: help, clear, mem, ticks, uptime, tasks, echo <text>, reboot.
 *  Line editing supports backspace. Output goes through kprintf (VGA + serial),
 *  so everything the shell prints is also captured on the serial console.
 * ===========================================================================*/
#include "include/shell.h"
#include "include/scheduler.h"
#include "include/keyboard.h"
#include "include/vga.h"
#include "include/kprintf.h"
#include "include/timer.h"
#include "include/memory.h"
#include "include/pmm.h"
#include "include/ports.h"
#include "lib/string.h"

#define LINE_MAX 128

static const char *state_name(task_state_t s) {
    switch (s) {
        case TASK_UNUSED:     return "unused";
        case TASK_READY:      return "ready";
        case TASK_RUNNING:    return "running";
        case TASK_BLOCKED:    return "blocked";
        case TASK_TERMINATED: return "dead";
        default:              return "?";
    }
}

static void cmd_help(void) {
    kprintf("Commands:\n");
    kprintf("  help            this message\n");
    kprintf("  clear           clear the screen\n");
    kprintf("  mem             heap + physical memory stats\n");
    kprintf("  ticks           raw timer tick count\n");
    kprintf("  uptime          seconds since boot\n");
    kprintf("  tasks           list scheduler tasks\n");
    kprintf("  echo <text>     print text back\n");
    kprintf("  reboot          reset the machine\n");
}

static void cmd_mem(void) {
    kprintf("heap:     %u bytes free\n", (u32)heap_bytes_free());
    kprintf("physical: %u/%u frames free (%u KiB free)\n",
            pmm_frames_free(), pmm_frames_total(),
            pmm_frames_free() * (FRAME_SIZE / 1024));
}

static void cmd_tasks(void) {
    kprintf("id  name      state    (* = current)\n");
    for (u32 i = 0; i < MAX_TASKS; i++) {
        const task_t *t = scheduler_task(i);
        if (!t || t->state == TASK_UNUSED) continue;
        kprintf("%u   %s\t%s%s\n", t->id, t->name, state_name(t->state),
                ((i32)t->id == scheduler_current_id()) ? "  *" : "");
    }
}

static void cmd_reboot(void) {
    kprintf("Rebooting...\n");
    /* Pulse the 8042 keyboard-controller reset line -> CPU reset. */
    u8 status;
    do { status = inb(0x64); } while (status & 0x02);   /* wait for input buffer */
    outb(0x64, 0xFE);
    for (;;) __asm__ volatile ("hlt");                  /* wait for the reset */
}

static void execute(const char *line) {
    /* Skip leading spaces. */
    while (*line == ' ') line++;
    if (*line == '\0') return;

    if (strcmp(line, "help") == 0)        cmd_help();
    else if (strcmp(line, "clear") == 0)  vga_clear();
    else if (strcmp(line, "mem") == 0)    cmd_mem();
    else if (strcmp(line, "ticks") == 0)  kprintf("%u ticks\n", timer_ticks());
    else if (strcmp(line, "uptime") == 0) kprintf("%u seconds\n", timer_ticks() / 100);
    else if (strcmp(line, "tasks") == 0)  cmd_tasks();
    else if (strcmp(line, "reboot") == 0) cmd_reboot();
    else if (strncmp(line, "echo ", 5) == 0) kprintf("%s\n", line + 5);
    else if (strcmp(line, "echo") == 0)   kprintf("\n");
    else kprintf("unknown command: %s  (try 'help')\n", line);
}

void shell_run(void) {
    char line[LINE_MAX];
    u32  len = 0;

    kprintf("\nMini-OS shell. Type 'help'.\n");
    kprintf("> ");

    for (;;) {
        if (!keyboard_has_input()) {
            task_yield();                   /* nothing to do: give up the CPU */
            continue;
        }

        char c = keyboard_getchar();
        if (c == '\n') {
            kprintf("\n");
            line[len] = '\0';
            execute(line);
            len = 0;
            kprintf("> ");
        } else if (c == '\b') {
            if (len > 0) { len--; vga_putchar('\b'); }
        } else if (len < LINE_MAX - 1) {
            line[len++] = c;
            kprintf("%c", c);               /* echo to VGA + serial */
        }
    }
}
