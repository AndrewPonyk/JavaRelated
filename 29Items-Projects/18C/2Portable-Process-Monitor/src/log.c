/* log.c — levelled, thread-safe stderr logging.
 *
 * The output of a single log call is serialised with an SRW lock so that lines
 * from the net server's accept thread cannot interleave with the main loop's. */
#include <stdarg.h>
#include <stdio.h>
#include <time.h>
#include "ppmon/log.h"

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
static SRWLOCK g_lock = SRWLOCK_INIT; /* statically initialised; no init call */
#endif

static ppmon_log_level_t g_level = PPMON_LOG_INFO;

static const char *level_tag(ppmon_log_level_t l) {
    switch (l) {
    case PPMON_LOG_TRACE:
        return "TRACE";
    case PPMON_LOG_DEBUG:
        return "DEBUG";
    case PPMON_LOG_INFO:
        return "INFO ";
    case PPMON_LOG_WARN:
        return "WARN ";
    case PPMON_LOG_ERROR:
        return "ERROR";
    default:
        return "?????";
    }
}

void ppmon_log_set_level(ppmon_log_level_t level) {
    g_level = level;
}

void ppmon_log(ppmon_log_level_t level, const char *module, const char *fmt, ...) {
    if (level < g_level || level >= PPMON_LOG_OFF) return;

    time_t now = time(NULL);
    struct tm tmv;
#if defined(_WIN32)
    localtime_s(&tmv, &now);
#else
    localtime_r(&now, &tmv);
#endif
    char ts[20];
    strftime(ts, sizeof(ts), "%Y-%m-%d %H:%M:%S", &tmv);

    va_list ap;
    va_start(ap, fmt);
#if defined(_WIN32)
    AcquireSRWLockExclusive(&g_lock);
#endif
    fprintf(stderr, "%s [%s] %-8s ", ts, level_tag(level), module ? module : "-");
    vfprintf(stderr, fmt, ap);
    fputc('\n', stderr);
#if defined(_WIN32)
    ReleaseSRWLockExclusive(&g_lock);
#endif
    va_end(ap);
}
