#!/usr/bin/env bash
# scripts/build.sh — Linux/macOS developer build helper.
# Usage:  ./scripts/build.sh [preset]   (default: dev-cpu)
set -euo pipefail

PRESET="${1:-default}"

# The default preset is dependency-light (pure C++ + vendored SQLite + fetched
# GoogleTest); no vcpkg required. Use the "full" preset for OpenCV/CUDA/Python.

echo "==> Configuring (preset: ${PRESET})"
cmake --preset "${PRESET}"

echo "==> Building"
cmake --build --preset "${PRESET}"

if [[ "${PRESET}" == "default" || "${PRESET}" == "debug" ]]; then
    echo "==> Testing"
    ctest --preset "${PRESET}"
fi
