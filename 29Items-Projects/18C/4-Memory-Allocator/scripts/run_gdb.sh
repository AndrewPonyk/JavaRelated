#!/usr/bin/env bash
#
# Launch a target under GDB with handy allocator breakpoints preset.
# Usage: scripts/run_gdb.sh [target] [args...]
# Default target: build/bin/demo  (build it first with `make debug`)
#
set -euo pipefail

TARGET="${1:-build/bin/demo}"
if [ $# -gt 0 ]; then shift; fi

if ! command -v gdb >/dev/null 2>&1; then
    echo "error: gdb not found. On Windows use WSL2/Linux (see docs/TECH-NOTES.md)." >&2
    exit 127
fi

exec gdb -q \
    -ex "break mem_malloc" \
    -ex "break free_list_coalesce" \
    -ex "echo \n[gdb] breakpoints set on mem_malloc and free_list_coalesce.\n" \
    -ex "run $*" \
    --args "$TARGET" "$@"
