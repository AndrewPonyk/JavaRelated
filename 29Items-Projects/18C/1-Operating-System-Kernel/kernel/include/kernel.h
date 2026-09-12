/*
 * kernel.h — Global kernel facilities: logging, assertions, panic.
 */
#ifndef KERNEL_KERNEL_H
#define KERNEL_KERNEL_H

#include "types.h"

/* ---- Build configuration (overridable via -D at compile time) ---- */
#ifndef KERNEL_VERSION
#define KERNEL_VERSION "0.1.0-dev"
#endif

#ifndef PAGE_SIZE
#define PAGE_SIZE      4096UL                /* also defined in memory.h (guarded) */
#endif
#define KERNEL_VBASE   0xFFFFFFFF80000000UL /* higher-half load base */

/* ---- Logging ---------------------------------------------------- */
typedef enum {
    LOG_DEBUG = 0,
    LOG_INFO,
    LOG_WARN,
    LOG_ERROR,
} log_level_t;

/* kprintf: minimal formatted output to VGA + serial. See lib/printf.c */
int  kprintf(const char *fmt, ...);
void klog(log_level_t level, const char *fmt, ...);

#define KLOG_DEBUG(...) klog(LOG_DEBUG, __VA_ARGS__)
#define KLOG_INFO(...)  klog(LOG_INFO,  __VA_ARGS__)
#define KLOG_WARN(...)  klog(LOG_WARN,  __VA_ARGS__)
#define KLOG_ERROR(...) klog(LOG_ERROR, __VA_ARGS__)

/* ---- Panic & assertions ----------------------------------------- */
__attribute__((noreturn))
void panic(const char *fmt, ...);

#ifdef DEBUG
#define KASSERT(cond)                                                     \
    do {                                                                  \
        if (!(cond)) {                                                    \
            panic("assertion failed: %s (%s:%d)", #cond, __FILE__, __LINE__); \
        }                                                                 \
    } while (0)
#else
#define KASSERT(cond) ((void)0)
#endif

/* ---- Freestanding libc (kernel/lib/string.c) -------------------- */
void  *memset(void *dst, int c, size_t n);
void  *memcpy(void *dst, const void *src, size_t n);
void  *memmove(void *dst, const void *src, size_t n);
int    memcmp(const void *a, const void *b, size_t n);
size_t strlen(const char *s);
int    strcmp(const char *a, const char *b);

/* ---- Local interrupt control ------------------------------------ */
/* Real cli/sti in the kernel; no-ops when compiled into the host unit tests
 * (where these privileged instructions would fault). */
#ifdef KERNEL_HOSTTEST
static inline void local_irq_disable(void) {}
static inline void local_irq_enable(void)  {}
#else
static inline void local_irq_disable(void) { __asm__ volatile("cli" ::: "memory"); }
static inline void local_irq_enable(void)  { __asm__ volatile("sti" ::: "memory"); }
#endif

/* Compiler hints */
#define likely(x)   __builtin_expect(!!(x), 1)
#define unlikely(x) __builtin_expect(!!(x), 0)
#define UNUSED(x)   ((void)(x))

#endif /* KERNEL_KERNEL_H */
