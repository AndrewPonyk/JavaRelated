/*
 * log.h — Levelled, thread-safe logging facade (stderr sink).
 *
 * All diagnostics go to stderr so metrics/CSV on stdout remain pipe-clean.
 * Secrets (tokens, full command lines) must never be passed to these macros.
 */
#ifndef PPMON_LOG_H
#define PPMON_LOG_H

#include "ppmon/ppmon.h"

typedef enum ppmon_log_level {
    PPMON_LOG_TRACE = 0,
    PPMON_LOG_DEBUG,
    PPMON_LOG_INFO,
    PPMON_LOG_WARN,
    PPMON_LOG_ERROR,
    PPMON_LOG_OFF
} ppmon_log_level_t;

/* Set the minimum level that will be emitted (default INFO). */
void ppmon_log_set_level(ppmon_log_level_t level);

/* Core formatted log entry; prefer the macros below. */
void ppmon_log(ppmon_log_level_t level, const char *module, const char *fmt, ...);

#define LOG_TRACE(mod, ...) ppmon_log(PPMON_LOG_TRACE, (mod), __VA_ARGS__)
#define LOG_DEBUG(mod, ...) ppmon_log(PPMON_LOG_DEBUG, (mod), __VA_ARGS__)
#define LOG_INFO(mod, ...) ppmon_log(PPMON_LOG_INFO, (mod), __VA_ARGS__)
#define LOG_WARN(mod, ...) ppmon_log(PPMON_LOG_WARN, (mod), __VA_ARGS__)
#define LOG_ERROR(mod, ...) ppmon_log(PPMON_LOG_ERROR, (mod), __VA_ARGS__)

#endif /* PPMON_LOG_H */
