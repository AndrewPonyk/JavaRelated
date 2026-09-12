/*
 * syscall.h — System call numbers and dispatch.
 *
 * This is the ONLY contract between Ring 3 (userspace) and Ring 0 (kernel).
 * Every argument crossing it is validated in syscall.c before use.
 */
#ifndef KERNEL_SYSCALL_H
#define KERNEL_SYSCALL_H

#include "types.h"

/* Stable syscall ABI numbers — append only, never renumber. */
#define SYS_read    0
#define SYS_write   1
#define SYS_open    2
#define SYS_close   3
#define SYS_exit    4
#define SYS_getpid  5
#define SYS_yield   6
#define SYS_spawn   7
#define SYS_MAX     8

/* Standard file descriptors. */
#define STDIN_FILENO   0
#define STDOUT_FILENO  1
#define STDERR_FILENO  2

/* Register-frame view passed to the dispatcher by the syscall entry stub. */
typedef struct syscall_frame {
    uint64_t num;                  /* syscall number (rax) */
    uint64_t arg0, arg1, arg2;     /* rdi, rsi, rdx */
    uint64_t arg3, arg4, arg5;     /* r10, r8, r9 */
} syscall_frame_t;

/* Initialize MSRs/IDT for the syscall entry path. */
void    syscall_init(void);

/* Central dispatcher; returns the value placed in rax for userspace.
 * Negative values are -errno. */
int64_t syscall_dispatch(syscall_frame_t *frame);

#endif /* KERNEL_SYSCALL_H */
