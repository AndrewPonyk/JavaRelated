/* SPDX-License-Identifier: MIT
 *
 * platform/win/getopt.h — resolves <getopt.h> on Windows. Declares the
 * getopt_long surface the project's CLI parser uses; implemented in
 * platform/win/getopt.c.
 */
#ifndef NPA_WIN_GETOPT_H
#define NPA_WIN_GETOPT_H

#define no_argument       0
#define required_argument 1
#define optional_argument 2

struct option {
    const char *name;
    int         has_arg;
    int        *flag;
    int         val;
};

extern char *optarg;
extern int   optind;
extern int   opterr;
extern int   optopt;

int getopt_long(int argc, char *const argv[], const char *optstring,
                const struct option *longopts, int *longindex);

#endif /* NPA_WIN_GETOPT_H */
