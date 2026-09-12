/* SPDX-License-Identifier: MIT
 *
 * util/log.h — leveled, file-backed logging.
 *
 * CRITICAL: logs must NEVER go to stdout while the ncurses UI is active —
 * that corrupts the screen. Default sink is a file (or stderr before the TUI
 * starts). See ARCHITECTURE.md §2.6.
 */
#ifndef NPA_UTIL_LOG_H
#define NPA_UTIL_LOG_H

#include "common/types.h"

typedef enum {
    LOG_TRACE = 0,
    LOG_DEBUG,
    LOG_INFO,
    LOG_WARN,
    LOG_ERROR,
    LOG_FATAL,
} log_level_t;

/*
 * Initialize logging. If path is NULL, logs to stderr. Threshold messages
 * below `level` are dropped cheaply (before formatting). Safe to call once
 * at startup before any threads spawn.
 */
npa_result_t log_init(const char *path, log_level_t level);

/* Flush and close the log sink. Call during orderly shutdown. */
void log_shutdown(void);

/* Parse "trace"|"debug"|"info"|"warn"|"error" → level (defaults to INFO). */
log_level_t log_level_from_str(const char *s);

/* Core entry; prefer the macros below. */
void log_write(log_level_t level, const char *file, int line,
               const char *fmt, ...)
#if defined(__GNUC__) || defined(__clang__)
    __attribute__((format(printf, 4, 5)))
#endif
    ;

#define LOG_T(...) log_write(LOG_TRACE, __FILE__, __LINE__, __VA_ARGS__)
#define LOG_D(...) log_write(LOG_DEBUG, __FILE__, __LINE__, __VA_ARGS__)
#define LOG_I(...) log_write(LOG_INFO,  __FILE__, __LINE__, __VA_ARGS__)
#define LOG_W(...) log_write(LOG_WARN,  __FILE__, __LINE__, __VA_ARGS__)
#define LOG_E(...) log_write(LOG_ERROR, __FILE__, __LINE__, __VA_ARGS__)
#define LOG_F(...) log_write(LOG_FATAL, __FILE__, __LINE__, __VA_ARGS__)

#endif /* NPA_UTIL_LOG_H */
