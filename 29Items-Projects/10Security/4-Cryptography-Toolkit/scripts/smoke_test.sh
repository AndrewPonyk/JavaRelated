#!/usr/bin/env bash
# Post-deploy smoke test — used as a deploy gate in .github/workflows/deploy.yml.
# Usage: BASE=https://staging.example.com ./scripts/smoke_test.sh
set -euo pipefail

BASE="${BASE:-https://localhost}"
fail=0

check() {  # name, expected_status, curl args...
  local name="$1" want="$2"; shift 2
  local got
  got="$(curl -sk -o /dev/null -w '%{http_code}' "$@")"
  if [ "$got" = "$want" ]; then
    echo "PASS  $name ($got)"
  else
    echo "FAIL  $name (want $want, got $got)"; fail=1
  fi
}

check "health"            200 "$BASE/api/health"
check "tls-handshake"     200 "$BASE/api/tls13/handshake"
check "lessons-list"      200 "$BASE/api/lessons/"
check "aes-encrypt-422"   422 -X POST -H 'Content-Type: application/json' -d '{}' "$BASE/api/aes/encrypt"

# Real TLS 1.3 verification (not the simulation)
if command -v openssl >/dev/null; then
  host="$(echo "$BASE" | sed -E 's#https?://##; s#[:/].*##')"
  proto="$(echo | openssl s_client -connect "$host:443" -tls1_3 2>/dev/null | grep -c 'Protocol *: TLSv1.3' || true)"
  [ "$proto" -ge 1 ] && echo "PASS  edge speaks TLS 1.3" || { echo "FAIL  edge TLS 1.3"; fail=1; }
fi

exit "$fail"
