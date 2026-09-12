/* metrics.c — per-process metric sampling via PSAPI + kernel32 query APIs. */
#include <string.h>
#include "ppmon/metrics.h"
#include "ppmon/log.h"

#define MODULE "metrics"

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <psapi.h>

static uint64_t filetime_to_u64(FILETIME ft) {
    ULARGE_INTEGER u;
    u.LowPart  = ft.dwLowDateTime;
    u.HighPart = ft.dwHighDateTime;
    return u.QuadPart; /* 100ns units */
}

/* Best-effort base image name (e.g. "chrome.exe") into out->image_name. */
static void fill_image_name(HANDLE h, ppmon_sample_t *out) {
    WCHAR path[MAX_PATH];
    DWORD sz = (DWORD)(sizeof(path) / sizeof(path[0]));
    if (!QueryFullProcessImageNameW(h, 0, path, &sz) || sz == 0) return;

    /* Find the last path separator to isolate the base name. */
    WCHAR *base = path;
    for (DWORD i = 0; i < sz; ++i) {
        if (path[i] == L'\\' || path[i] == L'/') base = &path[i + 1];
    }
    int n = WideCharToMultiByte(CP_UTF8, 0, base, -1, out->image_name,
                                (int)sizeof(out->image_name), NULL, NULL);
    if (n <= 0) out->image_name[0] = '\0';
}
#endif /* _WIN32 */

ppmon_status_t ppmon_metrics_sample(uint32_t pid, ppmon_sample_t *out) {
    if (!out) return PPMON_ERR_INVALID_ARG;
    memset(out, 0, sizeof(*out));
    out->pid = pid;

#if defined(_WIN32)
    LARGE_INTEGER qpc;
    QueryPerformanceCounter(&qpc);
    out->captured_qpc = (uint64_t)qpc.QuadPart;

    HANDLE h = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, (DWORD)pid);
    if (!h) {
        DWORD err = GetLastError();
        if (err == ERROR_ACCESS_DENIED) return PPMON_ERR_ACCESS_DENIED;
        return PPMON_ERR_NOT_FOUND; /* exited between enumeration and sampling */
    }

    FILETIME ftCreate, ftExit, ftKernel, ftUser;
    if (GetProcessTimes(h, &ftCreate, &ftExit, &ftKernel, &ftUser)) {
        out->start_time_100ns  = filetime_to_u64(ftCreate);
        out->kernel_time_100ns = filetime_to_u64(ftKernel);
        out->user_time_100ns   = filetime_to_u64(ftUser);
    }

    PROCESS_MEMORY_COUNTERS_EX pmc;
    memset(&pmc, 0, sizeof(pmc));
    pmc.cb = sizeof(pmc);
    if (GetProcessMemoryInfo(h, (PROCESS_MEMORY_COUNTERS *)&pmc, sizeof(pmc))) {
        out->working_set_bytes = (uint64_t)pmc.WorkingSetSize;
        out->private_bytes     = (uint64_t)pmc.PrivateUsage;
    }

    IO_COUNTERS io;
    memset(&io, 0, sizeof(io));
    if (GetProcessIoCounters(h, &io)) {
        out->read_bytes  = io.ReadTransferCount;
        out->write_bytes = io.WriteTransferCount;
    }

    fill_image_name(h, out);
    CloseHandle(h);
    return PPMON_OK;
#else
    return PPMON_ERR_UNSUPPORTED;
#endif
}
