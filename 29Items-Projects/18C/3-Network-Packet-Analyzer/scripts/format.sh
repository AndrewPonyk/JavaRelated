#!/usr/bin/env bash
# format.sh — apply (or, with --check, verify) clang-format across the tree.
#   ./scripts/format.sh          # rewrite files in place
#   ./scripts/format.sh --check  # CI mode: fail if anything is mis-formatted
set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v clang-format >/dev/null 2>&1; then
    echo "error: clang-format not found (run scripts/install-deps.sh)" >&2
    exit 1
fi

FILES=$(find src tests include -name '*.c' -o -name '*.h')

if [ "${1:-}" = "--check" ]; then
    echo "==> clang-format --dry-run (check mode)"
    clang-format --dry-run --Werror $FILES
    echo "==> Formatting OK"
else
    echo "==> clang-format -i (rewriting in place)"
    clang-format -i $FILES
    echo "==> Done"
fi
