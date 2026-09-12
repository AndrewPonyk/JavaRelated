#!/usr/bin/env bash
# run_benchmarks.sh — reproducible benchmark driver.
#
# Builds the release library + bench tool, then runs the benchmark with
# methodology guards (core pinning, governor hint) and writes a CSV + charts.
#
# Usage:
#   scripts/run_benchmarks.sh [--out results.csv] [--repeats N] [--suite all]
#
# Honors .env (CC, AS, ARCH_FLAGS, CPU_PIN, BENCH_REPEATS, ...). See .env.example.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# shellcheck disable=SC1091
[ -f .env ] && . ./.env

OUT="${BENCH_OUT:-results.csv}"
REPEATS="${BENCH_REPEATS:-25}"
WARMUP="${BENCH_WARMUP:-5}"
SUITE="all"
CPU_PIN="${CPU_PIN:-}"

while [ $# -gt 0 ]; do
    case "$1" in
        --out)     OUT="$2"; shift 2 ;;
        --repeats) REPEATS="$2"; shift 2 ;;
        --warmup)  WARMUP="$2"; shift 2 ;;
        --suite)   SUITE="$2"; shift 2 ;;
        -h|--help) grep '^#' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "unknown arg: $1" >&2; exit 2 ;;
    esac
done

echo ">> building release bench tool"
make bench BUILD=release

# Methodology guards (best effort; require privileges / Linux).
PIN=()
if command -v taskset >/dev/null 2>&1 && [ -n "$CPU_PIN" ]; then
    echo ">> pinning to core $CPU_PIN (taskset)"
    PIN=(taskset -c "$CPU_PIN")
fi
if command -v cpupower >/dev/null 2>&1; then
    echo ">> (hint) set 'performance' governor & disable turbo for stable numbers" >&2
fi

echo ">> running benchmarks: suite=$SUITE repeats=$REPEATS warmup=$WARMUP -> $OUT"
"${PIN[@]}" ./build/release/perflib_bench \
    --suite "$SUITE" --repeats "$REPEATS" --warmup "$WARMUP" --out "$OUT"

echo ">> rendering charts"
if command -v python3 >/dev/null 2>&1; then
    python3 tools/plot_results.py "$OUT" --out charts || true
fi

echo ">> done. results: $OUT  charts: charts/"
