/*
 * log.h — Minimal leveled logging.
 *
 * Usage:   log_info("loaded %zu bytes at 0x%llx", n, (unsigned long long)addr);
 * Levels:  trace < debug < info < warn < error < fatal
 * The active level is set programmatically or from the CPUEMU_LOG_LEVEL env var.
 * Messages below the active level are dropped cheaply (no formatting cost path).
 */
#ifndef CPUEMU_COMMON_LOG_H
#define CPUEMU_COMMON_LOG_H

typedef enum {
    LOG_TRACE = 0,
    LOG_DEBUG,
    LOG_INFO,
    LOG_WARN,
    LOG_ERROR,
    LOG_FATAL
} log_level_t;

/* Set/get the active threshold. */
void        log_set_level(log_level_t level);
log_level_t log_get_level(void);

/* Initialize the level from the CPUEMU_LOG_LEVEL environment variable
 * (trace|debug|info|warn|error|fatal). No-op if unset/invalid. */
void log_init_from_env(void);

/* Lowercase name of a level ("info", ...). */
const char *log_level_name(log_level_t level);

/* Core sink — prefer the macros below. */
void log_log(log_level_t level, const char *file, int line, const char *fmt, ...)
#if defined(__GNUC__) || defined(__clang__)
    __attribute__((format(printf, 4, 5)))
#endif
    ;

#define log_trace(...) log_log(LOG_TRACE, __FILE__, __LINE__, __VA_ARGS__)
#define log_debug(...) log_log(LOG_DEBUG, __FILE__, __LINE__, __VA_ARGS__)
#define log_info(...)  log_log(LOG_INFO,  __FILE__, __LINE__, __VA_ARGS__)
#define log_warn(...)  log_log(LOG_WARN,  __FILE__, __LINE__, __VA_ARGS__)
#define log_error(...) log_log(LOG_ERROR, __FILE__, __LINE__, __VA_ARGS__)
#define log_fatal(...) log_log(LOG_FATAL, __FILE__, __LINE__, __VA_ARGS__)

#endif /* CPUEMU_COMMON_LOG_H */
