/*
 * bench_main.c — benchmark CLI.
 *
 *   perflib_bench [--suite all|matrix|string] [--repeats N] [--warmup N]
 *                 [--out results.csv]
 *
 * Emits schema-valid CSV (schema/benchmark_results.schema.json describes the
 * JSON form; the CSV columns mirror it) and a live human summary to stderr.
 */
#include "perflib/perflib.h"
#include "bench_util.h"
#include <string.h>
#include <stdlib.h>

static void usage(const char *prog)
{
    fprintf(stderr,
        "usage: %s [--suite all|matrix|string] [--repeats N] [--warmup N] "
        "[--out FILE]\n", prog);
}

int main(int argc, char **argv)
{
    const char *suite = "all";
    int repeats = 15, warmup = 3;
    const char *out = NULL;

    for (int i = 1; i < argc; ++i) {
        if      (!strcmp(argv[i], "--suite")   && i + 1 < argc) suite   = argv[++i];
        else if (!strcmp(argv[i], "--repeats") && i + 1 < argc) repeats = atoi(argv[++i]);
        else if (!strcmp(argv[i], "--warmup")  && i + 1 < argc) warmup  = atoi(argv[++i]);
        else if (!strcmp(argv[i], "--out")     && i + 1 < argc) out     = argv[++i];
        else if (!strcmp(argv[i], "-h") || !strcmp(argv[i], "--help")) { usage(argv[0]); return 0; }
        else { fprintf(stderr, "unknown argument: %s\n", argv[i]); usage(argv[0]); return 2; }
    }
    if (repeats < 1) repeats = 1;
    if (warmup  < 0) warmup  = 0;

    perflib_init();
    fprintf(stderr, "%s  (repeats=%d warmup=%d)\n",
            perflib_version_string(), repeats, warmup);
    if (!perflib_has_avx2()) {
        fprintf(stderr, "ERROR: AVX2 not detected on this CPU; benchmarks call "
                        "the AVX2 kernels directly and would fault. Aborting.\n");
        return 3;
    }

    FILE *f = out ? fopen(out, "w") : stdout;
    if (!f) { perror("fopen"); return 1; }
    bench_emit_csv_header(f);

    int do_matrix = !strcmp(suite, "all") || !strcmp(suite, "matrix");
    int do_string = !strcmp(suite, "all") || !strcmp(suite, "string");
    if (!do_matrix && !do_string) {
        fprintf(stderr, "unknown suite: %s\n", suite);
        if (out) fclose(f);
        return 2;
    }
    if (do_matrix) bench_matrix(f, repeats, warmup);
    if (do_string) bench_string(f, repeats, warmup);

    if (out) { fclose(f); fprintf(stderr, "\nwrote results to %s\n", out); }
    return 0;
}
