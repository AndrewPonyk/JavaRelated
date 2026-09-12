/* csv_export.c — RFC-4180 CSV writer. Escaping + I/O implemented. */
#include <stdlib.h>
#include <string.h>
#include "ppmon/csv_export.h"
#include "ppmon/log.h"

#define MODULE "csv"

struct ppmon_csv {
    FILE *fp;
};

ppmon_status_t ppmon_csv_escape(const char *field, char *out, size_t out_cap, size_t *needed) {
    if (!field || !out || !needed) return PPMON_ERR_INVALID_ARG;

    int must_quote = 0;
    for (const char *p = field; *p; ++p) {
        if (*p == ',' || *p == '"' || *p == '\n' || *p == '\r') {
            must_quote = 1;
            break;
        }
    }

    size_t w = 0;
#define PUT(ch)                                                                               \
    do {                                                                                      \
        if (w < out_cap) out[w] = (ch);                                                       \
        ++w;                                                                                  \
    } while (0)

    if (must_quote) PUT('"');
    for (const char *p = field; *p; ++p) {
        if (must_quote && *p == '"') PUT('"'); /* double internal quotes */
        PUT(*p);
    }
    if (must_quote) PUT('"');
    if (w < out_cap)
        out[w] = '\0';
    else if (out_cap)
        out[out_cap - 1] = '\0';

    *needed = w + 1; /* include NUL */
#undef PUT
    return (w + 1 > out_cap) ? PPMON_ERR_NO_MEMORY : PPMON_OK;
}

ppmon_status_t ppmon_csv_open(const char *path, ppmon_csv_t **out) {
    if (!path || !out) return PPMON_ERR_INVALID_ARG;
    ppmon_csv_t *c = calloc(1, sizeof(*c));
    if (!c) return PPMON_ERR_NO_MEMORY;

    /* Detect whether the file is new/empty before opening for append. */
    long existing_size = -1;
    FILE *probe        = fopen(path, "rb");
    if (probe) {
        fseek(probe, 0, SEEK_END);
        existing_size = ftell(probe);
        fclose(probe);
    }

    c->fp = fopen(path, "ab");
    if (!c->fp) {
        free(c);
        return PPMON_ERR_IO;
    }

    if (existing_size <= 0) {
        if (fprintf(c->fp, "%s\n", PPMON_CSV_HEADER) < 0) {
            fclose(c->fp);
            free(c);
            return PPMON_ERR_IO;
        }
    }
    *out = c;
    LOG_DEBUG(MODULE, "opened CSV %s", path);
    return PPMON_OK;
}

void ppmon_csv_close(ppmon_csv_t *c) {
    if (!c) return;
    if (c->fp) fclose(c->fp);
    free(c);
}

ppmon_status_t ppmon_csv_write_row(ppmon_csv_t *c, const char *timestamp_iso8601,
                                   const ppmon_proc_metrics_t *m) {
    if (!c || !c->fp || !timestamp_iso8601 || !m) return PPMON_ERR_INVALID_ARG;

    char name_esc[PPMON_MAX_IMAGE_NAME * 2 + 4];
    size_t needed     = 0;
    ppmon_status_t st = ppmon_csv_escape(m->image_name, name_esc, sizeof(name_esc), &needed);
    if (st != PPMON_OK) return st;

    int rc = fprintf(c->fp, "%s,%u,%s,%.2f,%llu,%llu,%llu,%llu\n", timestamp_iso8601, m->pid,
                     name_esc, m->cpu_percent, (unsigned long long)m->working_set_bytes,
                     (unsigned long long)m->private_bytes,
                     (unsigned long long)m->read_bytes_per_sec,
                     (unsigned long long)m->write_bytes_per_sec);
    return rc < 0 ? PPMON_ERR_IO : PPMON_OK;
}

ppmon_status_t ppmon_csv_flush(ppmon_csv_t *c) {
    if (!c || !c->fp) return PPMON_ERR_INVALID_ARG;
    return fflush(c->fp) == 0 ? PPMON_OK : PPMON_ERR_IO;
}
