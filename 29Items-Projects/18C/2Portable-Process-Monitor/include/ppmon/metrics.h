/*
 * metrics.h — Per-process metric model and PSAPI-backed sampling.
 *
 * The metrics module turns raw OS counters (GetProcessTimes, PSAPI memory info,
 * GetProcessIoCounters) into a plain, platform-free ppmon_sample_t. CPU percent
 * is computed by sample_store as a delta between two of these snapshots.
 */
#ifndef PPMON_METRICS_H
#define PPMON_METRICS_H

#include "ppmon/ppmon.h"

#define PPMON_MAX_IMAGE_NAME 260

/* One raw, point-in-time sample for a single process. Times are in 100ns
 * units (Windows FILETIME native), bytes are absolute. */
typedef struct ppmon_sample {
    uint32_t pid;
    uint64_t start_time_100ns; /* process creation time; used for PID-reuse detection */
    char image_name[PPMON_MAX_IMAGE_NAME];

    uint64_t kernel_time_100ns; /* cumulative kernel CPU time */
    uint64_t user_time_100ns;   /* cumulative user CPU time   */

    uint64_t working_set_bytes; /* PSAPI WorkingSetSize         */
    uint64_t private_bytes;     /* PSAPI PrivateUsage           */

    uint64_t read_bytes;  /* IO_COUNTERS.ReadTransferCount  */
    uint64_t write_bytes; /* IO_COUNTERS.WriteTransferCount */

    uint64_t captured_qpc; /* QueryPerformanceCounter at capture */
} ppmon_sample_t;

/* Derived, presentation-ready metrics for one process (computed from two raw
 * samples by sample_store). */
typedef struct ppmon_proc_metrics {
    uint32_t pid;
    char image_name[PPMON_MAX_IMAGE_NAME];
    double cpu_percent; /* 0..100 across all cores */
    uint64_t working_set_bytes;
    uint64_t private_bytes;
    uint64_t read_bytes_per_sec;
    uint64_t write_bytes_per_sec;
} ppmon_proc_metrics_t;

/*
 * Populate `out` with current PSAPI counters for `pid`.
 * Returns PPMON_ERR_ACCESS_DENIED for protected processes (caller should warn
 * and skip), PPMON_ERR_NOT_FOUND if the process exited mid-poll.
 *
 * TODO: implement via OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION) +
 *       GetProcessTimes + GetProcessMemoryInfo (PSAPI) + GetProcessIoCounters.
 */
ppmon_status_t ppmon_metrics_sample(uint32_t pid, ppmon_sample_t *out);

#endif /* PPMON_METRICS_H */
