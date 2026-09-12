#!/usr/bin/env bash
# lint.sh — run static analysis (clang-tidy + cppcheck) over the source tree.
# Exit non-zero if any tool reports an issue (used as a CI gate).
set -euo pipefail
cd "$(dirname "$0")/.."

SRCS=$(find src -name '*.c')
INCLUDES=(-Isrc -Iinclude)
status=0

if command -v clang-tidy >/dev/null 2>&1; then
    echo "==> clang-tidy"
    # Compile-time feature macros so headers expose the same surface as the build.
    clang-tidy $SRCS -- -std=c11 "${INCLUDES[@]}" \
        -DHAVE_LIBPCAP -DNPA_WITH_NCURSES || status=1
else
    echo "!! clang-tidy not found; skipping (install via scripts/install-deps.sh)"
fi

if command -v cppcheck >/dev/null 2>&1; then
    echo "==> cppcheck"
    cppcheck --enable=warning,portability,performance \
        --inline-suppr --error-exitcode=1 --quiet \
        --std=c11 "${INCLUDES[@]}" src || status=1
else
    echo "!! cppcheck not found; skipping"
fi

if [ "$status" -ne 0 ]; then
    echo "==> Lint FAILED"
else
    echo "==> Lint clean"
fi
exit "$status"
