#!/usr/bin/env bash
# lcov coverage + per-layer threshold enforcement (docs/TECH-NOTES.md §3.2).
#
# A single global coverage number hides the truth for this codebase on purpose:
# domain/services and core/utils carry the logic that actually protects the
# product and are held to a much higher bar than presentation/. Enforcing that
# per-layer, not just running `flutter test --coverage` and eyeballing a
# percentage, is the point of this script — a number nobody enforces is
# decoration (TECH-NOTES §3.2).
set -euo pipefail

cd "$(dirname "$0")/.."

echo "Running flutter test --coverage..." >&2
flutter test --coverage

LCOV_FILE="coverage/lcov.info"
if [[ ! -f "$LCOV_FILE" ]]; then
  echo "error: $LCOV_FILE not produced." >&2
  exit 1
fi

# path-prefix -> minimum line coverage percentage.
declare -A THRESHOLDS=(
  ["lib/domain/services/"]=90
  ["lib/core/utils/"]=95
  ["lib/data/repositories/"]=75
  ["lib/presentation/"]=40
)

failed=0

for prefix in "${!THRESHOLDS[@]}"; do
  threshold="${THRESHOLDS[$prefix]}"

  # Sum LF (lines found) / LH (lines hit) across every SF: record under this
  # prefix. `flutter test --coverage` writes SF: with the host's native path
  # separator (backslashes on Windows) — normalise to forward slashes before
  # matching, or every prefix silently matches zero files on Windows.
  read -r lf lh < <(awk -v prefix="$prefix" '
    /^SF:/ {
      path = substr($0, 4)
      gsub(/\\/, "/", path)
      in_scope = index(path, prefix) == 1
    }
    in_scope && /^LF:/ { lf += substr($0, 4) }
    in_scope && /^LH:/ { lh += substr($0, 4) }
    END { print lf+0, lh+0 }
  ' "$LCOV_FILE")

  if [[ "$lf" -eq 0 ]]; then
    echo "warn: no coverage data found under $prefix (no files matched, or all untested)." >&2
    continue
  fi

  pct=$(awk -v lh="$lh" -v lf="$lf" 'BEGIN { printf "%.1f", (lh/lf)*100 }')
  status="ok"
  if awk -v pct="$pct" -v threshold="$threshold" 'BEGIN { exit !(pct+0 < threshold) }'; then
    status="BELOW THRESHOLD"
    failed=1
  fi

  printf '%-28s %6s%% (target %s%%) — %s\n' "$prefix" "$pct" "$threshold" "$status" >&2
done

if [[ "$failed" -ne 0 ]]; then
  echo "coverage.sh: one or more layers are below their threshold." >&2
  exit 1
fi

echo "coverage.sh: all layers meet their threshold." >&2
