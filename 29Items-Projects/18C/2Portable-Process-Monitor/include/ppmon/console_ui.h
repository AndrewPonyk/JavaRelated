/*
 * console_ui.h — Terminal rendering of the top-N process table (Presentation).
 *
 * Renders to stderr-independent stdout chrome only in interactive mode; in CSV
 * / piped mode the UI is suppressed so stdout stays machine-readable.
 */
#ifndef PPMON_CONSOLE_UI_H
#define PPMON_CONSOLE_UI_H

#include "ppmon/ppmon.h"
#include "ppmon/metrics.h"
#include "ppmon/pdh_counters.h"

typedef struct ppmon_ui ppmon_ui_t;

/* Initialise the console (enable VT processing, query window size). */
ppmon_status_t ppmon_ui_init(ppmon_ui_t **out, uint32_t top_n);
void ppmon_ui_destroy(ppmon_ui_t *ui);

/*
 * Render one frame: a system summary line plus a sorted top-N table.
 * TODO: sort by cpu_percent desc, refresh-in-place via VT cursor control.
 */
ppmon_status_t ppmon_ui_render(ppmon_ui_t *ui, const ppmon_system_metrics_t *sys,
                               const ppmon_proc_metrics_t *rows, size_t count);

#endif /* PPMON_CONSOLE_UI_H */
