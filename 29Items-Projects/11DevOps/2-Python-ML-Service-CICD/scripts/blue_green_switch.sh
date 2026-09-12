#!/usr/bin/env bash
# Blue-green traffic switch for the fraud-detection API on AKS.
#
# Verifies the target deployment is fully rolled out, smoke-tests it directly
# via `kubectl port-forward` (bypassing the Service, which still points at the
# old color), then flips the Service selector to the target color.
#
# Usage:
#   ./blue_green_switch.sh <blue|green> [namespace]
#
# Arguments:
#   TARGET_COLOR   Required. "blue" or "green".
#   NAMESPACE      Optional. Defaults to "fraud-detection".
#
# Environment:
#   SMOKE_LOCAL_PORT   Local port used for the port-forward (default 18000).
set -euo pipefail

TARGET_COLOR="${1:?Usage: $0 <blue|green> [namespace]}"
NAMESPACE="${2:-fraud-detection}"
SERVICE="fraud-api"
DEPLOYMENT="fraud-api-${TARGET_COLOR}"
LOCAL_PORT="${SMOKE_LOCAL_PORT:-18000}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ "${TARGET_COLOR}" != "blue" && "${TARGET_COLOR}" != "green" ]]; then
  echo "ERROR: TARGET_COLOR must be 'blue' or 'green', got '${TARGET_COLOR}'" >&2
  exit 1
fi

echo ">>> Verifying rollout of deployment/${DEPLOYMENT} in namespace '${NAMESPACE}'..."
kubectl -n "${NAMESPACE}" rollout status "deployment/${DEPLOYMENT}" --timeout=180s

READY_REPLICAS="$(kubectl -n "${NAMESPACE}" get deployment "${DEPLOYMENT}" \
  -o jsonpath='{.status.readyReplicas}')"
if [[ -z "${READY_REPLICAS}" || "${READY_REPLICAS}" == "0" ]]; then
  echo "ERROR: deployment/${DEPLOYMENT} has no ready replicas; scale it up first." >&2
  exit 1
fi

PREVIOUS_COLOR="$(kubectl -n "${NAMESPACE}" get service "${SERVICE}" \
  -o jsonpath='{.spec.selector.color}')"
echo ">>> Service '${SERVICE}' currently routes to color: ${PREVIOUS_COLOR}"

if [[ "${PREVIOUS_COLOR}" == "${TARGET_COLOR}" ]]; then
  echo ">>> Service already points at '${TARGET_COLOR}'; nothing to do."
  exit 0
fi

PF_PID=""
cleanup() {
  if [[ -n "${PF_PID}" ]] && kill -0 "${PF_PID}" 2>/dev/null; then
    kill "${PF_PID}" 2>/dev/null || true
    wait "${PF_PID}" 2>/dev/null || true
  fi
}
trap cleanup EXIT

echo ">>> Port-forwarding deployment/${DEPLOYMENT} to 127.0.0.1:${LOCAL_PORT}..."
kubectl -n "${NAMESPACE}" port-forward "deployment/${DEPLOYMENT}" \
  "${LOCAL_PORT}:8000" >/dev/null 2>&1 &
PF_PID=$!

# Wait for the tunnel to come up (max ~30s).
TUNNEL_UP=0
for _ in $(seq 1 30); do
  if curl -fsS --max-time 2 "http://127.0.0.1:${LOCAL_PORT}/health/live" >/dev/null 2>&1; then
    TUNNEL_UP=1
    break
  fi
  sleep 1
done
if [[ "${TUNNEL_UP}" != "1" ]]; then
  echo "ERROR: port-forward to deployment/${DEPLOYMENT} never became reachable." >&2
  exit 1
fi

echo ">>> Running smoke test against candidate color '${TARGET_COLOR}'..."
python "${SCRIPT_DIR}/smoke_test.py" --base-url "http://127.0.0.1:${LOCAL_PORT}"

echo ">>> Smoke test passed. Patching Service selector to color=${TARGET_COLOR}..."
kubectl -n "${NAMESPACE}" patch service "${SERVICE}" --type merge \
  -p "{\"spec\":{\"selector\":{\"app\":\"fraud-api\",\"color\":\"${TARGET_COLOR}\"}}}"

echo ""
echo ">>> Switch complete: '${SERVICE}' now routes to color=${TARGET_COLOR} (previous: ${PREVIOUS_COLOR})"
echo ">>> To roll back, run:"
echo "    kubectl -n ${NAMESPACE} patch service ${SERVICE} --type merge -p '{\"spec\":{\"selector\":{\"app\":\"fraud-api\",\"color\":\"${PREVIOUS_COLOR}\"}}}'"
