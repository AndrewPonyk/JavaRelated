/*
 * pdh_counters.h — System-wide performance counters via PDH.
 *
 * Wraps a PDH query holding a handful of English (locale-independent) counters.
 * The first collection is discarded internally as a warm-up so rate counters
 * return valid data on the first read the caller sees.
 */
#ifndef PPMON_PDH_COUNTERS_H
#define PPMON_PDH_COUNTERS_H

#include "ppmon/ppmon.h"

/* Snapshot of system-wide counters for one poll. */
typedef struct ppmon_system_metrics {
    double cpu_total_percent;     /* \Processor(_Total)\% Processor Time     */
    uint64_t mem_available_bytes; /* \Memory\Available Bytes                 */
    uint64_t mem_committed_bytes; /* \Memory\Committed Bytes                 */
    double disk_queue_length;     /* \PhysicalDisk(_Total)\Avg. Disk Queue   */
} ppmon_system_metrics_t;

typedef struct ppmon_pdh ppmon_pdh_t;

/*
 * Open a PDH query and add the English counters above.
 *
 * TODO: PdhOpenQuery + PdhAddEnglishCounter (NOT PdhAddCounter — locale safe).
 *       Perform one PdhCollectQueryData warm-up so the first read is valid.
 */
ppmon_status_t ppmon_pdh_open(ppmon_pdh_t **out);
void ppmon_pdh_close(ppmon_pdh_t *p);

/*
 * Collect and read the current system metrics.
 * TODO: PdhCollectQueryData + PdhGetFormattedCounterValue per counter.
 */
ppmon_status_t ppmon_pdh_collect(ppmon_pdh_t *p, ppmon_system_metrics_t *out);

#endif /* PPMON_PDH_COUNTERS_H */
