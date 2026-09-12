/* pdh_counters.c — system-wide counters via PDH.
 *
 * Uses PdhAddEnglishCounter so the hard-coded counter paths work regardless of
 * the OS display language. A counter that fails to add is left absent (its
 * field stays 0) rather than failing the whole query. */
#include <stdlib.h>
#include <string.h>
#include "ppmon/pdh_counters.h"
#include "ppmon/log.h"

#define MODULE "pdh"

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <pdh.h>
#include <pdhmsg.h>
#endif

struct ppmon_pdh {
#if defined(_WIN32)
    PDH_HQUERY query;
    PDH_HCOUNTER cpu;
    PDH_HCOUNTER mem_available;
    PDH_HCOUNTER mem_committed;
    PDH_HCOUNTER disk_queue;
#endif
    int warmed_up;
};

#if defined(_WIN32)
static void add_counter(PDH_HQUERY q, const wchar_t *path, PDH_HCOUNTER *out) {
    PDH_STATUS s = PdhAddEnglishCounterW(q, path, 0, out);
    if (s != ERROR_SUCCESS) {
        *out = NULL;
        LOG_WARN(MODULE, "counter unavailable (0x%08lX): %ls", (unsigned long)s, path);
    }
}

static double read_double(PDH_HCOUNTER c) {
    if (!c) return 0.0;
    PDH_FMT_COUNTERVALUE v;
    if (PdhGetFormattedCounterValue(c, PDH_FMT_DOUBLE, NULL, &v) == ERROR_SUCCESS)
        return v.doubleValue;
    return 0.0;
}

static uint64_t read_large(PDH_HCOUNTER c) {
    if (!c) return 0;
    PDH_FMT_COUNTERVALUE v;
    if (PdhGetFormattedCounterValue(c, PDH_FMT_LARGE, NULL, &v) == ERROR_SUCCESS)
        return (uint64_t)v.largeValue;
    return 0;
}
#endif

ppmon_status_t ppmon_pdh_open(ppmon_pdh_t **out) {
    if (!out) return PPMON_ERR_INVALID_ARG;
    ppmon_pdh_t *p = calloc(1, sizeof(*p));
    if (!p) return PPMON_ERR_NO_MEMORY;
#if defined(_WIN32)
    if (PdhOpenQueryW(NULL, 0, &p->query) != ERROR_SUCCESS) {
        free(p);
        LOG_ERROR(MODULE, "PdhOpenQuery failed");
        return PPMON_ERR_OS;
    }
    add_counter(p->query, L"\\Processor(_Total)\\% Processor Time", &p->cpu);
    add_counter(p->query, L"\\Memory\\Available Bytes", &p->mem_available);
    add_counter(p->query, L"\\Memory\\Committed Bytes", &p->mem_committed);
    add_counter(p->query, L"\\PhysicalDisk(_Total)\\Avg. Disk Queue Length", &p->disk_queue);

    /* Warm-up: rate counters yield no data until the second collection. */
    PdhCollectQueryData(p->query);
    p->warmed_up = 1;
    LOG_DEBUG(MODULE, "PDH query opened");
#endif
    *out = p;
    return PPMON_OK;
}

void ppmon_pdh_close(ppmon_pdh_t *p) {
    if (!p) return;
#if defined(_WIN32)
    if (p->query) PdhCloseQuery(p->query);
#endif
    free(p);
}

ppmon_status_t ppmon_pdh_collect(ppmon_pdh_t *p, ppmon_system_metrics_t *out) {
    if (!p || !out) return PPMON_ERR_INVALID_ARG;
    memset(out, 0, sizeof(*out));
#if defined(_WIN32)
    PDH_STATUS s = PdhCollectQueryData(p->query);
    if (s != ERROR_SUCCESS) {
        LOG_WARN(MODULE, "PdhCollectQueryData failed: 0x%08lX", (unsigned long)s);
        return PPMON_ERR_OS;
    }
    out->cpu_total_percent   = read_double(p->cpu);
    out->mem_available_bytes = read_large(p->mem_available);
    out->mem_committed_bytes = read_large(p->mem_committed);
    out->disk_queue_length   = read_double(p->disk_queue);
    return PPMON_OK;
#else
    return PPMON_ERR_UNSUPPORTED;
#endif
}
