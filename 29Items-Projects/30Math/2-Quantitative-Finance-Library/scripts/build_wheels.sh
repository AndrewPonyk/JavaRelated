#!/usr/bin/env bash
# Build release wheels locally (Linux wheels require Docker for manylinux).
# CI does this in release.yml; this script is for local verification only.
set -euo pipefail
cd "$(dirname "$0")/.."

pip install cibuildwheel==2.19.*
cibuildwheel --output-dir wheelhouse

echo "==> Built wheels:"
ls -lh wheelhouse/
