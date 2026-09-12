/*
 * process_enum.h — System-wide process enumeration via NtQuerySystemInformation.
 *
 * One enumeration pass returns every process, avoiding per-PID snapshot churn.
 * The internal buffer is reused across polls and only regrown on
 * STATUS_INFO_LENGTH_MISMATCH.
 */
#ifndef PPMON_PROCESS_ENUM_H
#define PPMON_PROCESS_ENUM_H

#include "ppmon/ppmon.h"

/* Opaque enumerator holding a reusable query buffer. */
typedef struct ppmon_enum ppmon_enum_t;

/* Minimal identity returned per process from the enumeration pass. */
typedef struct ppmon_proc_id {
    uint32_t pid;
    uint32_t parent_pid;
    uint64_t start_time_100ns;
    char image_name[260];
} ppmon_proc_id_t;

/* Create / destroy an enumerator (owns the growable NT query buffer). */
ppmon_status_t ppmon_enum_create(ppmon_enum_t **out);
void ppmon_enum_destroy(ppmon_enum_t *e);

/*
 * Refresh the snapshot of all running processes.
 *
 * TODO: implement using NtQuerySystemInformation(SystemProcessInformation, ...)
 *       with the two-call sizing pattern and STATUS_INFO_LENGTH_MISMATCH retry.
 *       ntdll is resolved dynamically via GetProcAddress to keep the import clean.
 */
ppmon_status_t ppmon_enum_refresh(ppmon_enum_t *e);

/* Number of processes captured by the last successful refresh. */
size_t ppmon_enum_count(const ppmon_enum_t *e);

/* Access the i-th process identity from the last refresh (0..count-1). */
ppmon_status_t ppmon_enum_at(const ppmon_enum_t *e, size_t i, ppmon_proc_id_t *out);

/*
 * Attempt to enable SeDebugPrivilege for the current process so that metrics can
 * be sampled from processes owned by other users / higher integrity levels.
 * Returns PPMON_ERR_ACCESS_DENIED when the privilege is not held by the token
 * (the caller should continue with reduced visibility rather than abort).
 */
ppmon_status_t ppmon_enable_debug_privilege(void);

#endif /* PPMON_PROCESS_ENUM_H */
