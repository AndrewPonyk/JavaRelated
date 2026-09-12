/*
 * shell.c — Minimal userspace shell (the OS "frontend").
 *
 * Runs entirely in Ring 3 and talks to the kernel ONLY through syscall wrappers
 * (usyscall.h). Demonstrates the request/response loop, plus the OS analogue of
 * "loading" and "error" states: a blocking read and per-command error reporting.
 *
 * Supported builtins: help, echo <text>, cat <file>, write <file> <text>, exit.
 */
#include "../lib/usyscall.h"

#define LINE_MAX 128

/* --- tiny freestanding helpers (no libc in userspace either) --- */
static unsigned long ustrlen(const char *s)
{
    unsigned long n = 0;
    while (s[n]) n++;
    return n;
}
static void print(const char *s) { sys_write(STDOUT_FILENO, s, ustrlen(s)); }

static int streq(const char *a, const char *b)
{
    while (*a && *b && *a == *b) { a++; b++; }
    return *a == *b;
}

/* Read a line from stdin. Returns length. This is the "loading" state — the
 * process blocks here until the kernel delivers input. */
static int read_line(char *buf, int max)
{
    int n = 0;
    while (n < max - 1) {
        char c;
        if (sys_read(STDIN_FILENO, &c, 1) <= 0) break;
        if (c == '\n') break;
        buf[n++] = c;
    }
    buf[n] = '\0';
    return n;
}

/* Split off the first token; returns pointer to the remaining args (or ""). */
static char *split(char *line)
{
    char *p = line;
    while (*p && *p != ' ') p++;
    if (*p == ' ') { *p = '\0'; return p + 1; }
    return p; /* points at the terminating NUL */
}

static void cmd_cat(const char *path)
{
    long fd = sys_open(path, O_RDONLY);
    if (fd < 0) { print("cat: cannot open file\n"); return; }   /* error state */
    char buf[64];
    long r;
    while ((r = sys_read((int)fd, buf, sizeof(buf))) > 0)
        sys_write(STDOUT_FILENO, buf, (unsigned long)r);
    sys_close((int)fd);
    print("\n");
}

static void cmd_write(char *args)
{
    char *text = split(args);                 /* args="<file>", text="<text>" */
    long fd = sys_open(args, O_WRONLY | O_CREAT);
    if (fd < 0) { print("write: cannot open file\n"); return; }
    sys_write((int)fd, text, ustrlen(text));
    sys_close((int)fd);
    print("ok\n");
}

void shell_main(void)
{
    char line[LINE_MAX];
    print("microkernel shell — type 'help'\n");

    for (;;) {
        print("$ ");
        if (read_line(line, LINE_MAX) == 0) continue;

        char *args = split(line);             /* line=cmd, args=rest */

        if (streq(line, "help")) {
            print("builtins: help, echo, cat <f>, write <f> <t>, exit\n");
        } else if (streq(line, "echo")) {
            print(args); print("\n");
        } else if (streq(line, "cat")) {
            cmd_cat(args);
        } else if (streq(line, "write")) {
            cmd_write(args);
        } else if (streq(line, "exit")) {
            print("bye\n");
            sys_exit(0);
        } else {
            print("unknown command: "); print(line); print("\n");
        }
    }
}

/* Entry point the kernel jumps to when it starts this program. */
void _start(void)
{
    shell_main();
    sys_exit(0);
}
