#!/usr/bin/env bash
# Convenience build wrapper for POSIX systems.
#   ./scripts/build.sh            # configure + build + test (Release)
#   ./scripts/build.sh debug      # Debug build
set -euo pipefail

BUILD_TYPE="Release"
BUILD_DIR="build"
if [[ "${1:-}" == "debug" ]]; then
  BUILD_TYPE="Debug"
  BUILD_DIR="build-debug"
fi

cmake -S . -B "${BUILD_DIR}" -DCMAKE_BUILD_TYPE="${BUILD_TYPE}"
cmake --build "${BUILD_DIR}" --parallel
ctest --test-dir "${BUILD_DIR}" --output-on-failure
echo "Done. CLI at ${BUILD_DIR}/minidb"
