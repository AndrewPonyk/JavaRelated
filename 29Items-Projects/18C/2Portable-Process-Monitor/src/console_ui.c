/* console_ui.c — interactive top-N table rendered to stdout via VT sequences.
 *
 * Rows are expected pre-sorted by the caller (CPU descending). The UI prints a
 * system summary line and then up to top_n process rows, refreshing in place
 * when the console supports virtual-terminal sequences. */
#include <stdlib.h>
#include <stdio.h>
#include "ppmon/console_ui.h"

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#endif

struct ppmon_ui {
    uint32_t top_n;
    int vt_enabled;
};

/* Render a byte count as a compact human-readable string (e.g. "1.2G"). */
static void human_bytes(uint64_t bytes, char *buf, size_t cap) {
    const char *units[] = {"B", "K", "M", "G", "T"};
    double v            = (double)bytes;
    int u               = 0;
    while (v >= 1024.0 && u < 4) {
        v /= 1024.0;
        ++u;
    }
    snprintf(buf, cap, "%6.1f%s", v, units[u]);
}

ppmon_status_t ppmon_ui_init(ppmon_ui_t **out, uint32_t top_n) {
    if (!out) return PPMON_ERR_INVALID_ARG;
    ppmon_ui_t *ui = calloc(1, sizeof(*ui));
    if (!ui) return PPMON_ERR_NO_MEMORY;
    ui->top_n = top_n ? top_n : 15;
#if defined(_WIN32)
    HANDLE hout = GetStdHandle(STD_OUTPUT_HANDLE);
    DWORD mode  = 0;
    if (hout != INVALID_HANDLE_VALUE && GetConsoleMode(hout, &mode)) {
        if (SetConsoleMode(hout, mode | ENABLE_VIRTUAL_TERMINAL_PROCESSING))
            ui->vt_enabled = 1;
    }
#endif
    *out = ui;
    return PPMON_OK;
}

void ppmon_ui_destroy(ppmon_ui_t *ui) {
    free(ui);
}

ppmon_status_t ppmon_ui_render(ppmon_ui_t *ui, const ppmon_system_metrics_t *sys,
                               const ppmon_proc_metrics_t *rows, size_t count) {
    if (!ui || !sys || (!rows && count)) return PPMON_ERR_INVALID_ARG;

    if (ui->vt_enabled) fputs("\x1b[H\x1b[2J", stdout); /* home + clear screen */

    char avail[16], commit[16];
    human_bytes(sys->mem_available_bytes, avail, sizeof(avail));
    human_bytes(sys->mem_committed_bytes, commit, sizeof(commit));
    printf("Portable Process Monitor   CPU %5.1f%%   Mem avail %s  committed %s  "
           "DiskQ %4.1f\n",
           sys->cpu_total_percent, avail, commit, sys->disk_queue_length);
    printf("%-6s %6s %9s %9s %10s %10s  %s\n", "PID", "CPU%", "WorkSet", "Private", "Read/s",
           "Write/s", "Image");
    puts("------------------------------------------------------------------------");

    size_t limit = count < ui->top_n ? count : ui->top_n;
    for (size_t i = 0; i < limit; ++i) {
        const ppmon_proc_metrics_t *m = &rows[i];
        char ws[16], pv[16], rd[16], wr[16];
        human_bytes(m->working_set_bytes, ws, sizeof(ws));
        human_bytes(m->private_bytes, pv, sizeof(pv));
        human_bytes(m->read_bytes_per_sec, rd, sizeof(rd));
        human_bytes(m->write_bytes_per_sec, wr, sizeof(wr));
        printf("%-6u %6.1f %9s %9s %10s %10s  %s\n", m->pid, m->cpu_percent, ws, pv, rd, wr,
               m->image_name);
    }
    fflush(stdout);
    return PPMON_OK;
}
