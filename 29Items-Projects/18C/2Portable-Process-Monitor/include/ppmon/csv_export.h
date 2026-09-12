/*
 * csv_export.h — RFC-4180 CSV writer with a stable, versioned schema.
 *
 * Column order is part of the public contract; never reorder existing columns,
 * only append. Fields containing comma/quote/newline are quote-escaped.
 */
#ifndef PPMON_CSV_EXPORT_H
#define PPMON_CSV_EXPORT_H

#include <stdio.h>
#include "ppmon/ppmon.h"
#include "ppmon/metrics.h"

/* Stable header line (also the schema version anchor). */
#define PPMON_CSV_HEADER                                                                      \
    "timestamp_iso8601,pid,image_name,cpu_percent,working_set_bytes,"                         \
    "private_bytes,read_bps,write_bps"

typedef struct ppmon_csv ppmon_csv_t;

/* Open `path` for append; writes the header iff the file is new/empty. */
ppmon_status_t ppmon_csv_open(const char *path, ppmon_csv_t **out);
void ppmon_csv_close(ppmon_csv_t *c);

/* Append one row for the given metrics at the given ISO-8601 timestamp. */
ppmon_status_t ppmon_csv_write_row(ppmon_csv_t *c, const char *timestamp_iso8601,
                                   const ppmon_proc_metrics_t *m);

/* Flush buffered rows to disk (called by the worker thread and on shutdown). */
ppmon_status_t ppmon_csv_flush(ppmon_csv_t *c);

/*
 * Escape `field` into `out` per RFC-4180 (wrap in quotes and double internal
 * quotes when the field contains comma, quote, CR or LF). Returns the number
 * of bytes that would be written (may exceed out_cap → truncation signalled by
 * PPMON_ERR_NO_MEMORY). Exposed for direct unit testing.
 */
ppmon_status_t ppmon_csv_escape(const char *field, char *out, size_t out_cap, size_t *needed);

#endif /* PPMON_CSV_EXPORT_H */
