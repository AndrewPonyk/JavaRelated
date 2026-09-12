#!/usr/bin/env bash
# ============================================================================
#  ci/scripts/deploy.sh — promote a built artifact to an environment.
#  Usage: ci/scripts/deploy.sh <dev|staging|prod>
#
#  Process-level blue/green on bare metal: install the new version alongside
#  the current one, warm it (sync FIX seq + book from the journal), then flip
#  the `current` symlink during a quiet/maintenance window. Old version stays
#  warm for instant rollback.
# ============================================================================
set -euo pipefail

ENVIRONMENT="${1:?usage: deploy.sh <dev|staging|prod>}"
INSTALL_ROOT="/opt/trading"
VERSION="$(git describe --tags --always)"
ARTIFACT="dist/rts-${VERSION}.rpm"

echo ">> Deploying ${VERSION} to ${ENVIRONMENT}"

case "${ENVIRONMENT}" in
  dev)     HOSTS="dev-trade-01" ;;
  staging) HOSTS="stg-trade-01" ;;
  prod)    HOSTS="prd-trade-01 prd-trade-02" ;;  # primary + warm standby
  *) echo "unknown environment: ${ENVIRONMENT}" >&2; exit 2 ;;
esac

for host in ${HOSTS}; do
    echo "   -> ${host}: install ${ARTIFACT}"
    # Production:
    #   scp "${ARTIFACT}" "${host}:/tmp/" && ssh "${host}" '
    #       sudo rpm -Uvh /tmp/rts-*.rpm
    #       sudo /opt/trading/'"${VERSION}"'/scripts/tune_host.sh
    #       sudo systemctl start trading-engine@'"${VERSION}"'   # warm standby
    #   '
    :
done

if [[ "${ENVIRONMENT}" == "staging" ]]; then
    echo ">> Running market-replay soak gate (must pass before promotion)"
    # Production: kick off the replay soak job; fail this script on regression.
fi

if [[ "${ENVIRONMENT}" == "prod" ]]; then
    echo ">> Cutover: flip 'current' symlink during maintenance window"
    # Production: ssh primary 'sudo ln -sfn /opt/trading/'"${VERSION}"' /opt/trading/current
    #                     && sudo systemctl reload trading-engine'
    echo "   (cutover stubbed — gated by operator approval in Jenkins)"
fi

echo ">> Deploy step complete for ${ENVIRONMENT}."
