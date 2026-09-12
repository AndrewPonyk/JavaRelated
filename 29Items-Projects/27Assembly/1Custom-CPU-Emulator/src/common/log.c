/*
 * log.c — Implementation of the leveled logger and status-string helper.
 */
#include "common/log.h"
#include "common/types.h"

#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#if defined(_WIN32)
#include <io.h>
#define cpuemu_isatty(fd) _isatty(fd)
#define cpuemu_fileno(f)  _fileno(f)
#else
#include <unistd.h>
#define cpuemu_isatty(fd) isatty(fd)
#define cpuemu_fileno(f)  fileno(f)
#endif

static log_level_t g_level = LOG_INFO;

static const char *const k_level_names[] = {
    "trace", "debug", "info", "warn", "error", "fatal"
};

/* ANSI colors per level (no-op if the terminal ignores them). */
static const char *const k_level_colors[] = {
    "\x1b[90m", "\x1b[36m", "\x1b[32m", "\x1b[33m", "\x1b[31m", "\x1b[35m"
};

void log_set_level(log_level_t level) {
    if (level >= LOG_TRACE && level <= LOG_FATAL) {
        g_level = level;
    }
}

log_level_t log_get_level(void) {
    return g_level;
}

const char *log_level_name(log_level_t level) {
    if (level < LOG_TRACE || level > LOG_FATAL) {
        return "?";
    }
    return k_level_names[level];
}

void log_init_from_env(void) {
    const char *env = getenv("CPUEMU_LOG_LEVEL");
    if (env == NULL || env[0] == '\0') {
        return;
    }
    for (int i = LOG_TRACE; i <= LOG_FATAL; ++i) {
        if (strcmp(env, k_level_names[i]) == 0) {
            g_level = (log_level_t)i;
            return;
        }
    }
    /* Unknown value: leave the default and warn once. */
    fprintf(stderr, "[warn] CPUEMU_LOG_LEVEL='%s' unrecognized; using '%s'\n",
            env, k_level_names[g_level]);
}

void log_log(log_level_t level, const char *file, int line, const char *fmt, ...) {
    if (level < g_level) {
        return; /* below threshold: skip all formatting work */
    }

    /* Short wall-clock timestamp for human scanning. */
    char timebuf[16];
    time_t now = time(NULL);
    struct tm tmv;
#if defined(_WIN32)
    localtime_s(&tmv, &now);
#else
    localtime_r(&now, &tmv);
#endif
    strftime(timebuf, sizeof timebuf, "%H:%M:%S", &tmv);

    /* Trim the path to just the basename for readability. */
    const char *base = file;
    for (const char *p = file; *p; ++p) {
        if (*p == '/' || *p == '\\') {
            base = p + 1;
        }
    }

    /* Only emit ANSI color when stderr is a terminal; never to a pipe/file. */
    static int use_color = -1;
    if (use_color < 0) {
        use_color = cpuemu_isatty(cpuemu_fileno(stderr)) ? 1 : 0;
    }
    const char *col = use_color ? k_level_colors[level] : "";
    const char *rst = use_color ? "\x1b[0m" : "";
    fprintf(stderr, "%s %s%-5s%s %s:%d: ",
            timebuf, col, k_level_names[level], rst, base, line);

    va_list args;
    va_start(args, fmt);
    vfprintf(stderr, fmt, args);
    va_end(args);

    fputc('\n', stderr);
    fflush(stderr);
}

/* ----- emu_status_str (declared in common/types.h) ---------------------- */
const char *emu_status_str(emu_status_t status) {
    switch (status) {
        case EMU_OK:                 return "ok";
        case EMU_ERR_NULL:           return "null argument";
        case EMU_ERR_MEM_BOUNDS:     return "memory access out of bounds";
        case EMU_ERR_DECODE:         return "instruction decode failure";
        case EMU_ERR_INVALID_OPCODE: return "invalid/unsupported opcode (#UD)";
        case EMU_ERR_DIV_ZERO:       return "divide by zero (#DE)";
        case EMU_ERR_IO:             return "host I/O error";
        case EMU_ERR_NOMEM:          return "out of host memory";
        case EMU_ERR_HALT:           return "halted (HLT)";
        default:                     return "unknown status";
    }
}
