/* SPDX-License-Identifier: MIT
 *
 * util/log.c — implementation of the leveled logger.
 *
 * Fully functional: leveled, timestamped, mutex-guarded, file- or stderr-backed.
 * Log rotation and async batching are intentionally out of scope (single-binary
 * tool); the hot path logs at TRACE so production runs never throttle on I/O.
 */
#include "util/log.h"

#include <pthread.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <time.h>

static FILE          *g_sink   = NULL;     /* defaults to stderr in log_init */
static log_level_t    g_level  = LOG_INFO;
static pthread_mutex_t g_lock  = PTHREAD_MUTEX_INITIALIZER;

static const char *level_tag(log_level_t l) {
    switch (l) {
        case LOG_TRACE: return "TRACE";
        case LOG_DEBUG: return "DEBUG";
        case LOG_INFO:  return "INFO ";
        case LOG_WARN:  return "WARN ";
        case LOG_ERROR: return "ERROR";
        case LOG_FATAL: return "FATAL";
        default:        return "?????";
    }
}

const char *npa_result_str(npa_result_t r) {
    /* Lives here so every TU linking the logger gets it for free. */
    switch (r) {
        case NPA_OK:              return "OK";
        case NPA_ERR_INVAL:       return "invalid argument";
        case NPA_ERR_NOMEM:       return "out of memory";
        case NPA_ERR_IO:          return "I/O error";
        case NPA_ERR_TRUNCATED:   return "truncated";
        case NPA_ERR_UNSUPPORTED: return "unsupported";
        case NPA_ERR_MALFORMED:   return "malformed";
        case NPA_ERR_AGAIN:       return "try again";
        case NPA_ERR_FULL:        return "full";
        case NPA_ERR_NOTFOUND:    return "not found";
        case NPA_ERR_INTERNAL:    return "internal error";
        default:                  return "unknown";
    }
}

log_level_t log_level_from_str(const char *s) {
    if (!s)                       return LOG_INFO;
    if (!strcmp(s, "trace"))      return LOG_TRACE;
    if (!strcmp(s, "debug"))      return LOG_DEBUG;
    if (!strcmp(s, "info"))       return LOG_INFO;
    if (!strcmp(s, "warn"))       return LOG_WARN;
    if (!strcmp(s, "error"))      return LOG_ERROR;
    if (!strcmp(s, "fatal"))      return LOG_FATAL;
    return LOG_INFO;
}

npa_result_t log_init(const char *path, log_level_t level) {
    g_level = level;
    if (path == NULL) {
        g_sink = stderr;
        return NPA_OK;
    }
    /* Append so logs survive across runs (no truncation of prior history). */
    g_sink = fopen(path, "a");
    if (!g_sink) {
        g_sink = stderr;                 /* fall back; never silently lose logs */
        return NPA_ERR_IO;
    }
    return NPA_OK;
}

void log_shutdown(void) {
    pthread_mutex_lock(&g_lock);
    if (g_sink && g_sink != stderr) {
        fclose(g_sink);
    }
    g_sink = NULL;
    pthread_mutex_unlock(&g_lock);
}

void log_write(log_level_t level, const char *file, int line,
               const char *fmt, ...) {
    if (level < g_level) {
        return;                          /* cheap drop before any formatting */
    }

    /* Short basename instead of the full path, for readable lines. */
    const char *base = file;
    for (const char *p = file; *p; ++p) {
        if (*p == '/' || *p == '\\') base = p + 1;
    }

    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    struct tm tm_buf;
#if defined(_WIN32)
    localtime_s(&tm_buf, &ts.tv_sec);
#else
    localtime_r(&ts.tv_sec, &tm_buf);
#endif
    char when[32];
    strftime(when, sizeof when, "%Y-%m-%d %H:%M:%S", &tm_buf);

    va_list ap;
    va_start(ap, fmt);

    pthread_mutex_lock(&g_lock);
    FILE *sink = g_sink ? g_sink : stderr;
    fprintf(sink, "%s.%03ld %s %s:%d ",
            when, ts.tv_nsec / 1000000L, level_tag(level), base, line);
    vfprintf(sink, fmt, ap);
    fputc('\n', sink);
    fflush(sink);                        /* flush per line so logs survive a crash */
    pthread_mutex_unlock(&g_lock);

    va_end(ap);
}
