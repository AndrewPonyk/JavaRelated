/*
 * usyscall.h — Userspace syscall wrappers (Ring 3 side of the ABI).
 *
 * Thin inline wrappers around the `syscall` instruction. This header is the
 * ONLY thing userspace needs to know about the kernel — the microkernel
 * boundary in code form.
 */
#ifndef USER_USYSCALL_H
#define USER_USYSCALL_H

#include "../../kernel/include/syscall.h"   /* for SYS_* numbers + types */

/* Issue a syscall with up to 3 arguments. Result (rax) returned to caller.
 * SysV/Linux-style: number in rax; args in rdi, rsi, rdx. */
static inline long __syscall3(long n, long a0, long a1, long a2)
{
    long ret;
    register long r10 __asm__("r10") = 0;
    __asm__ volatile("syscall"
                     : "=a"(ret)
                     : "a"(n), "D"(a0), "S"(a1), "d"(a2), "r"(r10)
                     : "rcx", "r11", "memory");
    return ret;
}

static inline long sys_write(int fd, const void *buf, unsigned long n)
{
    return __syscall3(SYS_write, fd, (long)buf, (long)n);
}
static inline long sys_read(int fd, void *buf, unsigned long n)
{
    return __syscall3(SYS_read, fd, (long)buf, (long)n);
}
static inline long sys_open(const char *path, int flags)
{
    return __syscall3(SYS_open, (long)path, flags, 0);
}
static inline long sys_close(int fd)
{
    return __syscall3(SYS_close, fd, 0, 0);
}
static inline long sys_getpid(void)
{
    return __syscall3(SYS_getpid, 0, 0, 0);
}
static inline void sys_exit(int code)
{
    __syscall3(SYS_exit, code, 0, 0);
    for (;;) { /* unreachable */ }
}

#endif /* USER_USYSCALL_H */
