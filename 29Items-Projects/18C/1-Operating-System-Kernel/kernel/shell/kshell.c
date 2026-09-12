/*
 * kshell.c — In-kernel interactive shell.
 *
 * Demonstrates the OS's user-facing surface against the real subsystems: VFS/
 * ramfs for files, the process table for `ps`, the PMM for `meminfo`, and the
 * keyboard driver for input. It runs as a kernel thread.
 *
 * (The ring-3 counterpart in user/shell/shell.c speaks the same command set
 * over the syscall ABI; see README "Scope" for why the shell runs in-kernel
 * here.)
 */
#include "../include/kernel.h"
#include "../include/sched.h"
#include "../include/vfs.h"
#include "../include/memory.h"

extern int      keyboard_getc(void);
extern uint64_t timer_ticks(void);
extern uint32_t timer_hz(void);

#define LINE_MAX 128

static const char *state_name(proc_state_t s)
{
    switch (s) {
        case PROC_UNUSED:  return "unused";
        case PROC_READY:   return "ready";
        case PROC_RUNNING: return "running";
        case PROC_BLOCKED: return "blocked";
        case PROC_ZOMBIE:  return "zombie";
        default:           return "?";
    }
}

/* Split off the first whitespace-delimited token; return the remainder. */
static char *split(char *s)
{
    while (*s && *s != ' ') s++;
    if (*s == ' ') { *s = '\0'; return s + 1; }
    return s;
}

static void cmd_cat(const char *path)
{
    int fd = vfs_open(path, O_RDONLY);
    if (fd < 0) { kprintf("cat: %s: no such file\n", path); return; }
    char buf[65];
    ssize_t r;
    while ((r = vfs_read(fd, buf, sizeof(buf) - 1)) > 0) {
        buf[r] = '\0';
        kprintf("%s", buf);
    }
    kprintf("\n");
    vfs_close(fd);
}

static void cmd_write(char *args)
{
    char *text = split(args);          /* args -> filename, text -> contents */
    if (!*args) { kprintf("usage: write <file> <text>\n"); return; }
    int fd = vfs_open(args, O_WRONLY | O_CREAT);
    if (fd < 0) { kprintf("write: cannot open %s\n", args); return; }
    ssize_t w = vfs_write(fd, text, strlen(text));
    vfs_close(fd);
    kprintf("wrote %d bytes to %s\n", (int)w, args);
}

static void cmd_ls(void)
{
    char names[16][VFS_NAME_MAX];
    int n = ramfs_list(names, 16);
    if (n == 0) { kprintf("(empty)\n"); return; }
    for (int i = 0; i < n; i++) kprintf("  %s\n", names[i]);
}

static void cmd_ps(void)
{
    pcb_t *t = process_table();
    kprintf("  PID  STATE    BAND  NAME\n");
    for (int i = 0; i < MAX_PROCESSES; i++) {
        if (t[i].state == PROC_UNUSED) continue;
        kprintf("  %d    %s   %u     %s\n",
                t[i].pid, state_name(t[i].state),
                t[i].dyn_priority, t[i].name);
    }
}

static void cmd_meminfo(void)
{
    size_t frames = pmm_free_count();
    kprintf("  free frames: %u  (%u KiB)\n",
            (unsigned long)frames, (unsigned long)(frames * 4));
}

static void cmd_uptime(void)
{
    uint64_t ticks = timer_ticks();
    uint32_t hz    = timer_hz();
    kprintf("  %u ticks (~%u ms) at %u Hz\n",
            (unsigned long)ticks,
            (unsigned long)(hz ? ticks * 1000 / hz : 0), hz);
}

/* Execute one command line. `line` is mutated in place. */
void kshell_exec(char *line)
{
    if (!*line) return;
    char *args = split(line);

    if      (strcmp(line, "help") == 0)
        kprintf("commands: help echo cat write ls ps meminfo uptime exit\n");
    else if (strcmp(line, "echo") == 0)    kprintf("%s\n", args);
    else if (strcmp(line, "cat") == 0)     cmd_cat(args);
    else if (strcmp(line, "write") == 0)   cmd_write(args);
    else if (strcmp(line, "ls") == 0)      cmd_ls();
    else if (strcmp(line, "ps") == 0)      cmd_ps();
    else if (strcmp(line, "meminfo") == 0) cmd_meminfo();
    else if (strcmp(line, "uptime") == 0)  cmd_uptime();
    else if (strcmp(line, "exit") == 0)    kprintf("(exit)\n");
    else kprintf("unknown command: %s (try 'help')\n", line);
}

/* Run a fixed list of commands — used by the boot demo / CI smoke test. */
void kshell_run_script(const char *const *cmds, int n)
{
    char buf[LINE_MAX];
    for (int i = 0; i < n; i++) {
        kprintf("ksh$ %s\n", cmds[i]);
        size_t j = 0;
        for (; cmds[i][j] && j < LINE_MAX - 1; j++) buf[j] = cmds[i][j];
        buf[j] = '\0';
        kshell_exec(buf);
    }
}

/* Interactive REPL: cooperatively polls the keyboard ring buffer. Used when a
 * real console is attached (QEMU with a display / serial input). */
void kshell_interactive(void)
{
    char line[LINE_MAX];
    size_t len = 0;
    kprintf("\nmicrokernel shell — type 'help'\nksh$ ");
    for (;;) {
        int c = keyboard_getc();
        if (c < 0) { sched_yield(); continue; }   /* nothing buffered: yield */

        if (c == '\n') {
            kprintf("\n");
            line[len] = '\0';
            if (strcmp(line, "exit") == 0) { kprintf("bye\n"); return; }
            kshell_exec(line);
            len = 0;
            kprintf("ksh$ ");
        } else if (c == '\b') {
            if (len) { len--; kprintf("\b \b"); }
        } else if (len < LINE_MAX - 1) {
            line[len++] = (char)c;
            kprintf("%c", c);
        }
    }
}
