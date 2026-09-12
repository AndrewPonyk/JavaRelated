/* SPDX-License-Identifier: MIT
 *
 * platform/win/pthread.h — minimal pthreads-on-Win32 shim.
 *
 * Resolves `#include <pthread.h>` on Windows (this dir is added to the include
 * path for MSVC builds) so the existing source — which uses pthread mutexes,
 * condition variables, and threads — compiles unchanged. Backed by SRWLOCK +
 * CONDITION_VARIABLE + _beginthreadex (Windows Vista+). Implements only the
 * subset the project uses.
 */
#ifndef NPA_WIN_PTHREAD_H
#define NPA_WIN_PTHREAD_H

#if !defined(_WIN32)
#  error "platform/win/pthread.h is for Windows builds only"
#endif

#ifndef WIN32_LEAN_AND_MEAN
#  define WIN32_LEAN_AND_MEAN
#endif
#ifndef NOMINMAX
#  define NOMINMAX
#endif
#include <windows.h>
#include <process.h>
#include <stdint.h>
#include <stdlib.h>

#pragma warning(disable : 4505)  /* unreferenced local function (header-only shim) */

typedef SRWLOCK            pthread_mutex_t;
typedef int               pthread_mutexattr_t;
typedef CONDITION_VARIABLE pthread_cond_t;
typedef int               pthread_condattr_t;
typedef uintptr_t         pthread_t;
typedef int               pthread_attr_t;

#define PTHREAD_MUTEX_INITIALIZER SRWLOCK_INIT

static __inline int pthread_mutex_init(pthread_mutex_t *m, const pthread_mutexattr_t *a) {
    (void)a; InitializeSRWLock(m); return 0;
}
static __inline int pthread_mutex_destroy(pthread_mutex_t *m) { (void)m; return 0; }
static __inline int pthread_mutex_lock(pthread_mutex_t *m)   { AcquireSRWLockExclusive(m); return 0; }
static __inline int pthread_mutex_unlock(pthread_mutex_t *m) { ReleaseSRWLockExclusive(m); return 0; }

static __inline int pthread_cond_init(pthread_cond_t *c, const pthread_condattr_t *a) {
    (void)a; InitializeConditionVariable(c); return 0;
}
static __inline int pthread_cond_destroy(pthread_cond_t *c)   { (void)c; return 0; }
static __inline int pthread_cond_wait(pthread_cond_t *c, pthread_mutex_t *m) {
    SleepConditionVariableSRW(c, m, INFINITE, 0); return 0;
}
static __inline int pthread_cond_signal(pthread_cond_t *c)    { WakeConditionVariable(c); return 0; }
static __inline int pthread_cond_broadcast(pthread_cond_t *c) { WakeAllConditionVariable(c); return 0; }

typedef struct { void *(*fn)(void *); void *arg; } npa_thr_args_;
static unsigned __stdcall npa_thr_tramp_(void *p) {
    npa_thr_args_ a = *(npa_thr_args_ *)p;
    free(p);
    a.fn(a.arg);
    return 0u;
}
static __inline int pthread_create(pthread_t *t, const pthread_attr_t *attr,
                                   void *(*fn)(void *), void *arg) {
    (void)attr;
    npa_thr_args_ *a = (npa_thr_args_ *)malloc(sizeof *a);
    if (!a) return -1;
    a->fn = fn; a->arg = arg;
    uintptr_t h = _beginthreadex(NULL, 0, npa_thr_tramp_, a, 0, NULL);
    if (!h) { free(a); return -1; }
    *t = (pthread_t)h;
    return 0;
}
static __inline int pthread_join(pthread_t t, void **retval) {
    (void)retval;
    WaitForSingleObject((HANDLE)t, INFINITE);
    CloseHandle((HANDLE)t);
    return 0;
}

#endif /* NPA_WIN_PTHREAD_H */
