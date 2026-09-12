/*
 * syscall.c — System call dispatch.
 *
 * The single audited gateway between Ring 3 and Ring 0. Every pointer/length
 * from userspace is validated here BEFORE it reaches a subsystem. This is the
 * project's primary security boundary (see ARCHITECTURE.md §2.5).
 */
#include "../include/kernel.h"
#include "../include/syscall.h"
#include "../include/sched.h"
#include "../include/vfs.h"

#define USER_PATH_MAX 256

/* A user buffer must lie entirely below the kernel's higher-half base. (Once a
 * ring-3 userspace exists, this also walks the caller's page tables to confirm
 * the range is mapped — see README "Scope".) */
static bool user_ptr_ok(uint64_t ptr, uint64_t len)
{
    if (len == 0) return true;
    if (ptr == 0) return false;
    if (ptr >= KERNEL_VBASE) return false;          /* points into kernel */
    if (ptr + len < ptr)     return false;          /* overflow */
    if (ptr + len >= KERNEL_VBASE) return false;
    return true;
}

void syscall_init(void)
{
    /* Dispatch is invoked directly by the trap/test path. Wiring the `syscall`
     * instruction (IA32_LSTAR/STAR/FMASK MSRs + an asm entry stub) belongs with
     * the ring-3 userspace work documented in the README. */
    KLOG_INFO("syscall: %d calls registered", SYS_MAX);
}

/* ---- Individual handlers ---------------------------------------- */

static int64_t sys_write(uint64_t fd, uint64_t buf, uint64_t n)
{
    if (!user_ptr_ok(buf, n)) return -EFAULT;
    return vfs_write((int)fd, (const void *)buf, (size_t)n);
}

static int64_t sys_read(uint64_t fd, uint64_t buf, uint64_t n)
{
    if (!user_ptr_ok(buf, n)) return -EFAULT;
    return vfs_read((int)fd, (void *)buf, (size_t)n);
}

static int64_t sys_open(uint64_t path, uint64_t flags)
{
    /* Validate one byte at a time, stopping at the NUL terminator, so a short
     * string near the top of user space is not rejected for a region it never
     * spans. Each byte's address is range-checked before it is dereferenced. */
    const char *p = (const char *)path;
    for (size_t i = 0; i < USER_PATH_MAX; i++) {
        if (!user_ptr_ok(path + i, 1)) return -EFAULT;
        if (p[i] == '\0') return vfs_open(p, (int)flags);
    }
    return -EINVAL;     /* path not terminated within USER_PATH_MAX */
}

static int64_t sys_exit(uint64_t code)
{
    sched_exit((int)code);
    return 0; /* unreachable */
}

/* ---- Dispatcher ------------------------------------------------- */

int64_t syscall_dispatch(syscall_frame_t *f)
{
    switch (f->num) {
        case SYS_read:   return sys_read(f->arg0, f->arg1, f->arg2);
        case SYS_write:  return sys_write(f->arg0, f->arg1, f->arg2);
        case SYS_open:   return sys_open(f->arg0, f->arg1);
        case SYS_close:  return vfs_close((int)f->arg0);
        case SYS_exit:   return sys_exit(f->arg0);
        case SYS_getpid: return sched_current() ? sched_current()->pid : -EPERM;
        case SYS_yield:  sched_yield(); return 0;
        /* SYS_spawn launches a ring-3 program — available once the userspace
         * ELF loader lands (README "Scope"); kernel threads use sched_spawn(). */
        case SYS_spawn:  return -EINVAL;
        default:
            KLOG_WARN("syscall: unknown number %lu", (unsigned long)f->num);
            return -EINVAL;
    }
}
