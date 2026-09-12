/* SPDX-License-Identifier: MIT
 *
 * platform/win/getopt.c — compact getopt_long for Windows (MSVC has none).
 *
 * Supports short options (with required args, joined or separate), long
 * options (--name, --name=value, --name value), and the flag/val convention.
 * No option bundling and no argv permutation — sufficient for this CLI, which
 * passes only options (no positional arguments). Compiles to nothing off-Win32.
 */
#if defined(_WIN32)

#include <getopt.h>
#include <string.h>

char *optarg = NULL;
int   optind = 1;
int   opterr = 1;
int   optopt = 0;

int getopt_long(int argc, char *const argv[], const char *optstring,
                const struct option *longopts, int *longindex) {
    optarg = NULL;
    if (optind >= argc) return -1;

    const char *arg = argv[optind];
    if (arg[0] != '-' || arg[1] == '\0') return -1;   /* not an option / "-" */

    if (arg[1] == '-') {                               /* --long */
        const char *name = arg + 2;
        if (*name == '\0') { optind++; return -1; }     /* "--" terminator */
        const char *eq = strchr(name, '=');
        size_t nlen = eq ? (size_t)(eq - name) : strlen(name);
        for (int i = 0; longopts && longopts[i].name; ++i) {
            if (strlen(longopts[i].name) == nlen &&
                strncmp(name, longopts[i].name, nlen) == 0) {
                optind++;
                if (longopts[i].has_arg == required_argument) {
                    if (eq)                 optarg = (char *)(eq + 1);
                    else if (optind < argc) optarg = (char *)argv[optind++];
                    else { optopt = longopts[i].val; return '?'; }
                }
                if (longindex) *longindex = i;
                if (longopts[i].flag) { *longopts[i].flag = longopts[i].val; return 0; }
                return longopts[i].val;
            }
        }
        optind++;
        return '?';                                     /* unknown long option */
    }

    int c = (unsigned char)arg[1];                      /* -x short option */
    const char *spec = (c == ':') ? NULL : strchr(optstring, c);
    if (!spec) { optind++; optopt = c; return '?'; }
    if (spec[1] == ':') {                               /* requires an argument */
        if (arg[2] != '\0') { optarg = (char *)(arg + 2); optind++; }
        else {
            optind++;
            if (optind < argc) optarg = (char *)argv[optind++];
            else { optopt = c; return '?'; }
        }
    } else {
        optind++;
    }
    return c;
}

#endif /* _WIN32 */
