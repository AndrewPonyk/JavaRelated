/* process_enum.c — system-wide enumeration via NtQuerySystemInformation.
 *
 * One call returns every process in a single buffer (reused across polls,
 * regrown only on STATUS_INFO_LENGTH_MISMATCH). NtQuerySystemInformation is
 * resolved dynamically from ntdll so we do not take a static import on a
 * semi-documented entry point. */
#include <stdlib.h>
#include <string.h>
#include "ppmon/process_enum.h"
#include "ppmon/log.h"

#define MODULE "enum"

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <winternl.h> /* UNICODE_STRING, NTSTATUS */

#ifndef STATUS_SUCCESS
#define STATUS_SUCCESS ((NTSTATUS)0x00000000L)
#endif
#ifndef STATUS_INFO_LENGTH_MISMATCH
#define STATUS_INFO_LENGTH_MISMATCH ((NTSTATUS)0xC0000004L)
#endif

#define PPMON_SYSTEM_PROCESS_INFORMATION_CLASS 5 /* SystemProcessInformation */

/* Full, layout-correct prefix of SYSTEM_PROCESS_INFORMATION (x64). We read only
 * up to InheritedFromUniqueProcessId; the fields are declared so the offsets of
 * the ones we use are exact across Windows builds. */
typedef struct PPMON_SPI {
    ULONG NextEntryOffset;
    ULONG NumberOfThreads;
    LARGE_INTEGER WorkingSetPrivateSize;
    ULONG HardFaultCount;
    ULONG NumberOfThreadsHighWatermark;
    ULONGLONG CycleTime;
    LARGE_INTEGER CreateTime;
    LARGE_INTEGER UserTime;
    LARGE_INTEGER KernelTime;
    UNICODE_STRING ImageName;
    LONG BasePriority;
    HANDLE UniqueProcessId;
    HANDLE InheritedFromUniqueProcessId;
    /* ... handle/session/memory/io counters follow; not needed here ... */
} PPMON_SPI;

typedef NTSTATUS(NTAPI *PFN_NtQuerySystemInformation)(ULONG SystemInformationClass,
                                                      PVOID SystemInformation,
                                                      ULONG SystemInformationLength,
                                                      PULONG ReturnLength);
#endif /* _WIN32 */

struct ppmon_enum {
    void *buffer;
    size_t buffer_size;
    ppmon_proc_id_t *ids;
    size_t count;
    size_t capacity;
#if defined(_WIN32)
    PFN_NtQuerySystemInformation query;
#endif
};

ppmon_status_t ppmon_enum_create(ppmon_enum_t **out) {
    if (!out) return PPMON_ERR_INVALID_ARG;
    ppmon_enum_t *e = calloc(1, sizeof(*e));
    if (!e) return PPMON_ERR_NO_MEMORY;
#if defined(_WIN32)
    HMODULE ntdll = GetModuleHandleW(L"ntdll.dll");
    if (!ntdll) {
        free(e);
        return PPMON_ERR_OS;
    }
    e->query = (PFN_NtQuerySystemInformation)GetProcAddress(ntdll, "NtQuerySystemInformation");
    if (!e->query) {
        free(e);
        return PPMON_ERR_UNSUPPORTED;
    }
    e->buffer_size = 256 * 1024; /* generous starting size to avoid early retries */
    e->buffer      = malloc(e->buffer_size);
    if (!e->buffer) {
        free(e);
        return PPMON_ERR_NO_MEMORY;
    }
#endif
    *out = e;
    return PPMON_OK;
}

void ppmon_enum_destroy(ppmon_enum_t *e) {
    if (!e) return;
    free(e->buffer);
    free(e->ids);
    free(e);
}

#if defined(_WIN32)
/* Append a parsed identity, growing the ids array as needed. */
static ppmon_status_t push_id(ppmon_enum_t *e, const PPMON_SPI *spi) {
    if (e->count == e->capacity) {
        size_t newcap       = e->capacity ? e->capacity * 2 : 256;
        ppmon_proc_id_t *ni = realloc(e->ids, newcap * sizeof(*ni));
        if (!ni) return PPMON_ERR_NO_MEMORY;
        e->ids      = ni;
        e->capacity = newcap;
    }
    ppmon_proc_id_t *id  = &e->ids[e->count];
    id->pid              = (uint32_t)(uintptr_t)spi->UniqueProcessId;
    id->parent_pid       = (uint32_t)(uintptr_t)spi->InheritedFromUniqueProcessId;
    id->start_time_100ns = (uint64_t)spi->CreateTime.QuadPart;
    id->image_name[0]    = '\0';

    if (spi->ImageName.Buffer && spi->ImageName.Length > 0) {
        int wlen = (int)(spi->ImageName.Length / sizeof(WCHAR));
        int n    = WideCharToMultiByte(CP_UTF8, 0, spi->ImageName.Buffer, wlen, id->image_name,
                                       (int)sizeof(id->image_name) - 1, NULL, NULL);
        if (n < 0) n = 0;
        id->image_name[n] = '\0';
    } else {
        /* PID 0 / the System process carry no image name; use a clear label. */
        strncpy(id->image_name, id->pid == 0 ? "System Idle Process" : "System",
                sizeof(id->image_name) - 1);
    }
    e->count++;
    return PPMON_OK;
}
#endif

ppmon_status_t ppmon_enum_refresh(ppmon_enum_t *e) {
    if (!e) return PPMON_ERR_INVALID_ARG;
#if defined(_WIN32)
    ULONG needed = 0;
    NTSTATUS st  = STATUS_SUCCESS;

    for (int attempt = 0; attempt < 6; ++attempt) {
        st = e->query(PPMON_SYSTEM_PROCESS_INFORMATION_CLASS, e->buffer, (ULONG)e->buffer_size,
                      &needed);
        if (st == STATUS_SUCCESS) break;
        if (st == STATUS_INFO_LENGTH_MISMATCH) {
            /* Grow with slack — the set can change between sizing and the call. */
            size_t newsize = (needed ? (size_t)needed : e->buffer_size) + 64 * 1024;
            void *nb       = realloc(e->buffer, newsize);
            if (!nb) return PPMON_ERR_NO_MEMORY;
            e->buffer      = nb;
            e->buffer_size = newsize;
            continue;
        }
        LOG_ERROR(MODULE, "NtQuerySystemInformation failed: 0x%08lX", (unsigned long)st);
        return PPMON_ERR_OS;
    }
    if (st != STATUS_SUCCESS) return PPMON_ERR_AGAIN;

    e->count = 0;
    BYTE *p  = (BYTE *)e->buffer;
    for (;;) {
        const PPMON_SPI *spi = (const PPMON_SPI *)p;
        ppmon_status_t rc    = push_id(e, spi);
        if (rc != PPMON_OK) return rc;
        if (spi->NextEntryOffset == 0) break;
        p += spi->NextEntryOffset;
    }
    LOG_TRACE(MODULE, "enumerated %zu processes", e->count);
    return PPMON_OK;
#else
    e->count = 0;
    return PPMON_ERR_UNSUPPORTED;
#endif
}

size_t ppmon_enum_count(const ppmon_enum_t *e) {
    return e ? e->count : 0;
}

ppmon_status_t ppmon_enum_at(const ppmon_enum_t *e, size_t i, ppmon_proc_id_t *out) {
    if (!e || !out) return PPMON_ERR_INVALID_ARG;
    if (i >= e->count) return PPMON_ERR_NOT_FOUND;
    *out = e->ids[i];
    return PPMON_OK;
}

ppmon_status_t ppmon_enable_debug_privilege(void) {
#if defined(_WIN32)
    HANDLE token = NULL;
    if (!OpenProcessToken(GetCurrentProcess(), TOKEN_ADJUST_PRIVILEGES | TOKEN_QUERY,
                          &token)) {
        return PPMON_ERR_OS;
    }
    LUID luid;
    ppmon_status_t result = PPMON_OK;
    if (!LookupPrivilegeValueA(NULL, SE_DEBUG_NAME, &luid)) { /* SE_DEBUG_NAME is narrow */
        result = PPMON_ERR_OS;
    } else {
        TOKEN_PRIVILEGES tp;
        tp.PrivilegeCount           = 1;
        tp.Privileges[0].Luid       = luid;
        tp.Privileges[0].Attributes = SE_PRIVILEGE_ENABLED;
        if (!AdjustTokenPrivileges(token, FALSE, &tp, sizeof(tp), NULL, NULL)) {
            result = PPMON_ERR_OS;
        } else if (GetLastError() == ERROR_NOT_ALL_ASSIGNED) {
            /* The token simply does not hold SeDebugPrivilege (non-elevated). */
            result = PPMON_ERR_ACCESS_DENIED;
        }
    }
    CloseHandle(token);
    return result;
#else
    return PPMON_ERR_UNSUPPORTED;
#endif
}
