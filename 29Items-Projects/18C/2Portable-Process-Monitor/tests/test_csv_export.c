/* test_csv_export.c — RFC-4180 escaping + a write/read-back round trip. */
#include "test_util.h"
#include "ppmon/csv_export.h"
#include <stdio.h>

static void test_escape(void) {
    char buf[64];
    size_t needed = 0;

    CHECK(ppmon_csv_escape("chrome", buf, sizeof(buf), &needed) == PPMON_OK);
    CHECK_EQ_STR(buf, "chrome");

    CHECK(ppmon_csv_escape("a,b", buf, sizeof(buf), &needed) == PPMON_OK);
    CHECK_EQ_STR(buf, "\"a,b\"");

    CHECK(ppmon_csv_escape("say \"hi\"", buf, sizeof(buf), &needed) == PPMON_OK);
    CHECK_EQ_STR(buf, "\"say \"\"hi\"\"\"");

    CHECK(ppmon_csv_escape("line1\nline2", buf, sizeof(buf), &needed) == PPMON_OK);
    CHECK_EQ_STR(buf, "\"line1\nline2\"");

    char tiny[3];
    CHECK(ppmon_csv_escape("toolong", tiny, sizeof(tiny), &needed) == PPMON_ERR_NO_MEMORY);
}

static void test_roundtrip(void) {
    const char *path = "ppmon_test_roundtrip.csv";
    remove(path);

    ppmon_csv_t *c = NULL;
    CHECK(ppmon_csv_open(path, &c) == PPMON_OK);

    ppmon_proc_metrics_t m;
    memset(&m, 0, sizeof(m));
    m.pid = 4321;
    strcpy(m.image_name, "we,ird.exe"); /* comma forces quoting */
    m.cpu_percent         = 12.50;
    m.working_set_bytes   = 1048576;
    m.private_bytes       = 2097152;
    m.read_bytes_per_sec  = 100;
    m.write_bytes_per_sec = 200;

    CHECK(ppmon_csv_write_row(c, "2026-06-17T00:00:00.000Z", &m) == PPMON_OK);
    CHECK(ppmon_csv_flush(c) == PPMON_OK);
    ppmon_csv_close(c);

    FILE *fp = fopen(path, "rb");
    CHECK(fp != NULL);
    if (fp) {
        char header[256] = {0}, row[256] = {0};
        CHECK(fgets(header, sizeof(header), fp) != NULL);
        CHECK(fgets(row, sizeof(row), fp) != NULL);
        fclose(fp);

        /* Header must match the published schema exactly. */
        CHECK(strncmp(header, PPMON_CSV_HEADER, strlen(PPMON_CSV_HEADER)) == 0);
        /* Row must carry the timestamp, pid, and the quoted, comma-bearing name. */
        CHECK(strstr(row, "2026-06-17T00:00:00.000Z") == row);
        CHECK(strstr(row, ",4321,") != NULL);
        CHECK(strstr(row, "\"we,ird.exe\"") != NULL);
        CHECK(strstr(row, "12.50") != NULL);
    }
    remove(path);
}

int main(void) {
    test_escape();
    test_roundtrip();
    TEST_MAIN_RETURN();
}
