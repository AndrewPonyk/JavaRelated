#!/usr/bin/env bash
# ============================================================================
#  ci/scripts/build.sh — configure, build, and test the engine.
#  The default build is dependency-free (C++20 + CMake/Ninja). Pass extra CMake
#  flags to enable optional features, e.g.:
#     ci/scripts/build.sh -DRTS_ENABLE_BOOST=ON
#  Usage: ci/scripts/build.sh [extra cmake args...]
# ============================================================================
set -euo pipefail

BUILD_DIR="${BUILD_DIR:-build}"
BUILD_TYPE="${BUILD_TYPE:-Release}"
GEN="${GEN:-Ninja}"

echo ">> Configure (${BUILD_TYPE}, ${GEN})"
cmake -S . -B "${BUILD_DIR}" -G "${GEN}" \
    -DCMAKE_BUILD_TYPE="${BUILD_TYPE}" -DRTS_BUILD_TESTS=ON "$@"

echo ">> Build"
cmake --build "${BUILD_DIR}" --parallel

echo ">> Test"
ctest --test-dir "${BUILD_DIR}" --output-on-failure

echo ">> Smoke run"
./"${BUILD_DIR}"/trading_engine config/trading_engine.yaml
./"${BUILD_DIR}"/backtest "" 4000

echo ">> Done."
