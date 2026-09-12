#!/usr/bin/env bash
# Post-deploy smoke test. Usage: ./scripts/smoke_test.sh https://service-url
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"

echo "Smoke testing ${BASE_URL}"

# 1. Liveness
code=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/health")
[ "$code" = "200" ] || { echo "FAIL: /health returned $code"; exit 1; }
echo "OK: /health"

# 2. Readiness (model loaded)
code=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/ready")
[ "$code" = "200" ] || { echo "FAIL: /ready returned $code"; exit 1; }
echo "OK: /ready"

echo "Smoke test passed."
