#!/usr/bin/env bash
#
# Run a target under Valgrind with full leak checking.
# Usage: scripts/run_valgrind.sh [target] [args...]
# Default target: build/bin/demo
#
set -euo pipefail

TARGET="${1:-build/bin/demo}"
if [ $# -gt 0 ]; then shift; fi

if ! command -v valgrind >/dev/null 2>&1; then
    echo "error: valgrind not found. On Windows use WSL2/Linux (see docs/TECH-NOTES.md)." >&2
    exit 127
fi

exec valgrind \
    --leak-check=full \
    --show-leak-kinds=all \
    --track-origins=yes \
    --errors-for-leak-kinds=definite,indirect \
    --error-exitcode=1 \
    "$TARGET" "$@"
