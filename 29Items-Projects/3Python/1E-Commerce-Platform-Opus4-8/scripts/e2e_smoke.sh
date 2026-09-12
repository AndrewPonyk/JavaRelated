#!/usr/bin/env bash
# Post-deploy smoke + Playwright E2E against the deployed environment.
# Usage: e2e_smoke.sh <base_url>
set -euo pipefail

BASE_URL="${1:?base url required}"

echo "Health check: $BASE_URL/healthz"
curl --fail --silent --show-error "$BASE_URL/healthz" > /dev/null
echo "OK"

# TODO: cd frontend && npx playwright test --config=e2e.config.ts
echo "TODO: run Playwright E2E suite against $BASE_URL"
