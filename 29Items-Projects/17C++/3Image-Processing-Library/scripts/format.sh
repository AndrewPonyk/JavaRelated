#!/usr/bin/env bash
# scripts/format.sh — apply clang-format in place across the codebase.
set -euo pipefail

CLANG_FORMAT="${CLANG_FORMAT:-clang-format}"

find include src apps bindings tests benchmarks \
    -regextype posix-extended -regex '.*\.(cpp|hpp|cu)' -print0 \
    | xargs -0 "${CLANG_FORMAT}" -i

echo "Formatted all C++/CUDA sources."
