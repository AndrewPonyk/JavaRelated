#!/usr/bin/env bash
# Post-deploy smoke test: availability + create/read/cancel round-trip + idempotency replay.
#
# Usage:
#   BASE_URL=https://orders-staging.example.com bash scripts/smoke-test.sh
#   MANAGEMENT_URL=http://localhost:8081 BASE_URL=http://localhost:8080 bash scripts/smoke-test.sh
#   AUTH_TOKEN=<jwt> BASE_URL=... bash scripts/smoke-test.sh     # when JWT is enforced
#
# MANAGEMENT_URL is optional: actuator lives on the management port, which is
# cluster-internal in Kubernetes — from outside the ALB only the API is visible.
# Requires: curl + (jq or python). Portable across GitHub runners, Linux, Git Bash.

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
MANAGEMENT_URL="${MANAGEMENT_URL:-}"
echo "Smoke-testing ${BASE_URL}"

fail() { echo "SMOKE TEST FAILED: $1" >&2; exit 1; }

AUTH_ARGS=()
[ -n "${AUTH_TOKEN:-}" ] && AUTH_ARGS=(-H "Authorization: Bearer ${AUTH_TOKEN}")

# Pick a Python interpreter (python3 on runners/Linux, python on Windows Git Bash)
PY="$(command -v python3 || command -v python || true)"

uuid() {
  if command -v uuidgen >/dev/null 2>&1; then uuidgen
  elif [ -n "${PY}" ]; then "${PY}" -c 'import uuid; print(uuid.uuid4())'
  else fail "need uuidgen or python to generate a UUID"
  fi
}

# json_field <name>  — reads JSON on stdin, prints the top-level field
json_field() {
  if command -v jq >/dev/null 2>&1; then
    jq -r --arg f "$1" '.[$f]'
  elif [ -n "${PY}" ]; then
    "${PY}" -c "import json,sys; print(json.load(sys.stdin)[sys.argv[1]])" "$1"
  else
    fail "need jq or python to parse JSON responses"
  fi
}

# 1. availability — the OpenAPI document is public (no auth) and served on the API port
curl -fsS "${BASE_URL}/v3/api-docs" -o /dev/null || fail "OpenAPI document is not reachable"
echo "✓ API reachable (/v3/api-docs)"

# 1b. readiness — only when the management port is reachable from where we run
if [ -n "${MANAGEMENT_URL}" ]; then
  curl -fsS "${MANAGEMENT_URL}/actuator/health/readiness" | grep -q '"UP"' \
    || fail "readiness probe is not UP"
  echo "✓ readiness UP (management port)"
fi

# 2. create an order
CUSTOMER_ID="$(uuid)"
CREATE_RESPONSE="$(curl -fsS -X POST "${BASE_URL}/api/v1/orders" \
  "${AUTH_ARGS[@]}" -H 'Content-Type: application/json' \
  -d "{\"customerId\": \"${CUSTOMER_ID}\",
       \"items\": [{\"sku\": \"SMOKE-1\", \"productName\": \"Smoke Test Item\",
                    \"quantity\": 2, \"unitPrice\": 9.99}]}")" \
  || fail "order creation returned an error"

ORDER_ID="$(echo "${CREATE_RESPONSE}" | json_field id)"
TOTAL="$(echo "${CREATE_RESPONSE}" | json_field totalAmount)"
[ "${ORDER_ID}" != "null" ] && [ -n "${ORDER_ID}" ] || fail "create response has no id: ${CREATE_RESPONSE}"
[ "${TOTAL}" = "19.98" ] || fail "unexpected total ${TOTAL} (expected 19.98)"
echo "✓ created order ${ORDER_ID} (total ${TOTAL})"

# 3. read it back
STATUS="$(curl -fsS "${AUTH_ARGS[@]}" "${BASE_URL}/api/v1/orders/${ORDER_ID}" | json_field status)"
[ "${STATUS}" = "NEW" ] || fail "expected status NEW, got ${STATUS}"
echo "✓ read back order in status ${STATUS}"

# 4. cancel (keeps environments tidy; exercises the state machine + DELETE path)
curl -fsS -X DELETE "${AUTH_ARGS[@]}" "${BASE_URL}/api/v1/orders/${ORDER_ID}" -o /dev/null \
  || fail "cancel failed"
echo "✓ cancelled smoke order"

# 5. idempotency: the same Idempotency-Key twice must replay (200), not duplicate
IDEMPOTENCY_KEY="$(uuid)"
IDEMPOTENT_BODY="{\"customerId\": \"${CUSTOMER_ID}\",
  \"items\": [{\"sku\": \"SMOKE-2\", \"productName\": \"Replay Item\", \"quantity\": 1, \"unitPrice\": 5.00}]}"
FIRST_ID="$(curl -fsS -X POST "${BASE_URL}/api/v1/orders" \
  "${AUTH_ARGS[@]}" -H 'Content-Type: application/json' -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
  -d "${IDEMPOTENT_BODY}" | json_field id)"
REPLAY_FILE="$(mktemp)"
REPLAY_CODE="$(curl -fsS -o "${REPLAY_FILE}" -w '%{http_code}' -X POST "${BASE_URL}/api/v1/orders" \
  "${AUTH_ARGS[@]}" -H 'Content-Type: application/json' -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
  -d "${IDEMPOTENT_BODY}")"
REPLAY_ID="$(json_field id < "${REPLAY_FILE}")"
rm -f "${REPLAY_FILE}"
[ "${REPLAY_CODE}" = "200" ] || fail "replay expected HTTP 200, got ${REPLAY_CODE}"
[ "${REPLAY_ID}" = "${FIRST_ID}" ] || fail "replay returned a different order (${REPLAY_ID} != ${FIRST_ID})"
curl -fsS -X DELETE "${AUTH_ARGS[@]}" "${BASE_URL}/api/v1/orders/${FIRST_ID}" -o /dev/null || true
echo "✓ idempotent replay returned the same order (${FIRST_ID})"

echo "SMOKE TEST PASSED"
