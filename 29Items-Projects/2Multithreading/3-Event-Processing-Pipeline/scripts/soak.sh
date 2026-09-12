#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Soak run: the same pipeline, repeatedly, under deliberately hostile settings.
#
# WHY THIS EXISTS. A concurrency bug that reproduces once in fifty runs will
# pass every unit test and every CI build, and then appear in the one run
# somebody cares about. The unit tests prove the logic; this script attacks the
# timing. The two are not substitutes.
#
# What it does differently from a normal run:
#   * unthrottled producer, so the queues stay at capacity and the producer
#     spends its life blocked in put() -- the state a drain bug needs;
#   * more consumer threads than cores, so the scheduler preempts mid-stage;
#   * shallow queues and large batches, minimising the buffering that hides a
#     mistake;
#   * N iterations with a DIFFERENT random seed each time, because a fixed seed
#     explores exactly one event stream;
#   * an interrupt injected into some iterations, so the Ctrl+C drain path is
#     covered too -- it is the path least exercised and most likely to lose
#     events.
#
# It fails on the FIRST bad iteration and keeps that iteration's log, because a
# summary saying "3 of 200 failed" without the logs is not actionable.
#
# Usage:
#   scripts/soak.sh                       # 20 iterations, 30s each
#   scripts/soak.sh --iterations 200 --duration 60
#   scripts/soak.sh --interrupt-every 5   # Ctrl+C every 5th iteration
#
# Exit codes:
#   0  every iteration reconciled and exited cleanly
#   1  an iteration failed; see the reported log file
#   2  bad arguments or environment
# ---------------------------------------------------------------------------
set -Eeuo pipefail

PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
JAR_PATH="$PROJECT_ROOT/target/event-processing-pipeline-1.0.0-SNAPSHOT.jar"
LOG_DIR="$PROJECT_ROOT/target/soak"

ITERATIONS=20
DURATION=30
INTERRUPT_EVERY=0
CONSUMER_THREADS=$(( $(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4) * 2 ))

die() { printf 'error: %s\n' "$*" >&2; exit 2; }

while [[ $# -gt 0 ]]; do
    case "$1" in
        --iterations)      ITERATIONS="${2:?}"; shift 2 ;;
        --duration)        DURATION="${2:?}"; shift 2 ;;
        --interrupt-every) INTERRUPT_EVERY="${2:?}"; shift 2 ;;
        --threads)         CONSUMER_THREADS="${2:?}"; shift 2 ;;
        -h|--help)         sed -n '2,36p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *)                 die "unknown argument '$1'" ;;
    esac
done

[[ "$ITERATIONS" -ge 1 ]] || die "--iterations must be >= 1"
[[ "$DURATION"   -ge 1 ]] || die "--duration must be >= 1 (seconds)"

# --- environment -----------------------------------------------------------
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
else
    JAVA_BIN="$(command -v java)" || die "no java on PATH and JAVA_HOME is unset"
fi

[[ -f "$JAR_PATH" ]] || die "jar not found at $JAR_PATH -- run scripts/run.sh --build first"

# The staging profile enables the control plane, which refuses to start without
# a token. Generating one here rather than requiring it means a soak never
# fails on a configuration detail after 20 minutes of running. Not a secret:
# it is bound to 127.0.0.1 and lives for one iteration.
if [[ -z "${PIPELINE_HTTP_TOKEN:-}" ]]; then
    if command -v openssl >/dev/null 2>&1; then
        PIPELINE_HTTP_TOKEN="$(openssl rand -hex 24)"
    else
        PIPELINE_HTTP_TOKEN="soak-$(date +%s)-$$-local-only-token"
    fi
    export PIPELINE_HTTP_TOKEN
fi

mkdir -p "$LOG_DIR"

printf '=== soak: %d iterations x %ds, %d consumer threads ===\n' \
    "$ITERATIONS" "$DURATION" "$CONSUMER_THREADS"
printf 'logs: %s\n\n' "$LOG_DIR"

cd "$PROJECT_ROOT"

failures=0
started_at=$(date +%s)

for (( i = 1; i <= ITERATIONS; i++ )); do
    # A different seed per iteration. A fixed seed makes every iteration
    # explore the same event stream, which is reproducibility at the cost of
    # coverage -- the opposite of what a soak is for. The seed is printed, so
    # a failing iteration is still reproducible on demand.
    seed=$(( started_at + i * 7919 ))
    log="$LOG_DIR/iteration-$(printf '%03d' "$i").log"

    args=(
        "--pipeline.env=staging"
        "--pipeline.random.seed=$seed"
        "--pipeline.duration.seconds=$DURATION"
        "--pipeline.event.count=0"
        "--pipeline.events.per.second=0"
        "--pipeline.consumer.threads=$CONSUMER_THREADS"
        "--pipeline.queue.capacity=8"
        "--pipeline.batch.size=512"
        # Port per iteration: the previous iteration's socket may still be in
        # TIME_WAIT, and "address already in use" would look like a pipeline
        # failure when it is only impatience.
        "--pipeline.http.port=$(( 18000 + i ))"
        "--pipeline.log.level=INFO"
    )

    interrupt=0
    if [[ "$INTERRUPT_EVERY" -gt 0 && $(( i % INTERRUPT_EVERY )) -eq 0 ]]; then
        interrupt=1
    fi

    printf 'iteration %3d/%d seed=%-12s' "$i" "$ITERATIONS" "$seed"
    [[ "$interrupt" -eq 1 ]] && printf 'interrupt '

    set +e
    if [[ "$interrupt" -eq 1 ]]; then
        # SIGINT part-way through, then wait. This is the whole point of the
        # interrupt path: the shutdown hook must drain, print a report and exit
        # 130 -- not 143, not 1, and not with lost events.
        "$JAVA_BIN" -XX:+UseParallelGC -Xmx1g -jar "$JAR_PATH" "${args[@]}" >"$log" 2>&1 &
        pid=$!
        sleep $(( DURATION / 2 + 1 ))
        kill -INT "$pid" 2>/dev/null
        wait "$pid"
        exit_code=$?
    else
        "$JAVA_BIN" -XX:+UseParallelGC -Xmx1g -jar "$JAR_PATH" "${args[@]}" >"$log" 2>&1
        exit_code=$?
    fi
    set -e

    # An interrupted iteration is expected to exit 130; a normal one, 0.
    expected=0
    [[ "$interrupt" -eq 1 ]] && expected=130

    # The exit code is necessary but not sufficient. It is produced by the same
    # code path as the reconciliation check, so a bug that weakens the check
    # weakens the exit code with it. MetricsSnapshot.toLogLine prints
    # `reconciled=true|false` independently, and that is what is asserted here
    # -- belt and braces on the one invariant that matters.
    if grep -q 'reconciled=false' "$log"; then
        reconciled=0
    else
        reconciled=1
    fi

    if [[ "$exit_code" -ne "$expected" ]]; then
        printf 'FAILED (exit %s, expected %s)\n' "$exit_code" "$expected"
        printf '\n--- last 40 lines of %s ---\n' "$log"
        tail -n 40 "$log"
        failures=$(( failures + 1 ))
        break
    elif [[ "$reconciled" -eq 0 ]]; then
        printf 'FAILED (exit %s but counters did not reconcile)\n' "$exit_code"
        printf '\n--- last 40 lines of %s ---\n' "$log"
        tail -n 40 "$log"
        failures=$(( failures + 1 ))
        break
    else
        # Keep only the passing iteration's report: 200 iterations of full INFO
        # logs is hundreds of megabytes nobody will read.
        grep -E '^(result|reason|elapsed|stage=|produced=)' "$log" > "$log.summary" || true
        mv "$log.summary" "$log"
        printf 'ok (exit %s)\n' "$exit_code"
    fi
done

elapsed=$(( $(date +%s) - started_at ))
# After a break, i is the failing iteration; after a clean loop it is
# ITERATIONS + 1. Either way i - 1 is the number that passed.
printf '\n=== soak finished in %ds: %d/%d iterations passed ===\n' \
    "$elapsed" "$(( i - 1 ))" "$ITERATIONS"

if [[ "$failures" -gt 0 ]]; then
    printf 'FAILED -- the failing iteration'\''s log was kept in %s\n' "$LOG_DIR"
    exit 1
fi

printf 'all iterations reconciled and exited cleanly\n'
exit 0
