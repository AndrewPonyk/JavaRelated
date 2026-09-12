#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Builds (if needed) and runs the Event Processing Pipeline.
#
# POSIX counterpart of scripts/run.ps1. Kept in step with it deliberately:
# the same profile switch, the same JVM flags, the same pass-through of
# --pipeline.* arguments, so a result reproduced on one platform means
# something on the other.
#
# Usage:
#   scripts/run.sh                                 # dev profile
#   scripts/run.sh --env prod                      # prod profile
#   scripts/run.sh --build --skip-tests            # force a fast rebuild
#   scripts/run.sh -- --pipeline.event.count=5000000
#   scripts/run.sh -- --help                       # every setting + exit codes
#
# Exit codes are passed through unchanged, because they are the contract:
#   0    success
#   1    run failed, or events were lost
#   2    invalid configuration -- nothing was started
#   130  interrupted (Ctrl+C) after a clean drain
# ---------------------------------------------------------------------------
set -Eeuo pipefail

# -e alone is not enough: without -o pipefail a failing `mvn` inside a pipe
# is masked by a successful `tee`, and the run continues against a stale jar.

PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
JAR_NAME="event-processing-pipeline-1.0.0-SNAPSHOT.jar"
JAR_PATH="$PROJECT_ROOT/target/$JAR_NAME"

PROFILE="dev"
FORCE_BUILD=0
SKIP_TESTS=0
APP_ARGS=()

die() { printf 'error: %s\n' "$*" >&2; exit 2; }

# --- arguments -------------------------------------------------------------
# Everything after a bare `--` belongs to the application. Two separate
# namespaces, so --env (ours) and --pipeline.env (its) never collide.
while [[ $# -gt 0 ]]; do
    case "$1" in
        --env)        PROFILE="${2:-}"; shift 2 || die "--env needs a value" ;;
        --env=*)      PROFILE="${1#*=}"; shift ;;
        --build)      FORCE_BUILD=1; shift ;;
        --skip-tests) SKIP_TESTS=1; shift ;;
        -h|--help-script)
            sed -n '2,25p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        --)           shift; APP_ARGS+=("$@"); break ;;
        *)            APP_ARGS+=("$1"); shift ;;
    esac
done

case "$PROFILE" in
    dev|staging|prod) ;;
    *) die "unknown profile '$PROFILE' (expected dev, staging or prod)" ;;
esac

# --- JDK 21 ----------------------------------------------------------------
# Checked first: a wrong JDK surfaces minutes later as a Maven error that
# points at the wrong thing.
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
    JAVA_BIN="$(command -v java)"
else
    die "no java on PATH and JAVA_HOME is unset; a JDK 21+ is required"
fi

JAVA_VERSION_LINE="$("$JAVA_BIN" -version 2>&1 | head -n 1)"
JAVA_MAJOR="$(printf '%s' "$JAVA_VERSION_LINE" | sed -n 's/.*version "\([0-9]*\).*/\1/p')"
if [[ -n "$JAVA_MAJOR" && "$JAVA_MAJOR" -lt 21 ]]; then
    die "JDK 21+ required but found $JAVA_MAJOR ($JAVA_VERSION_LINE)"
fi

printf 'JDK      : %s\n' "$JAVA_VERSION_LINE"
printf 'profile  : %s\n' "$PROFILE"

# --- build -----------------------------------------------------------------
# Rebuild when forced, when the jar is missing, or when a source file is newer
# than it. `mvn package` dominates the wall-clock time of a one-second dev run,
# so it is worth skipping when nothing changed.
needs_build=$FORCE_BUILD
if [[ ! -f "$JAR_PATH" ]]; then
    needs_build=1
elif [[ -n "$(find "$PROJECT_ROOT/src" "$PROJECT_ROOT/pom.xml" -newer "$JAR_PATH" -print -quit 2>/dev/null)" ]]; then
    printf 'sources newer than jar; rebuilding\n'
    needs_build=1
fi

if [[ "$needs_build" -eq 1 ]]; then
    mvn_args=(-q package)
    [[ "$SKIP_TESTS" -eq 1 ]] && mvn_args+=(-DskipTests)
    printf 'building : mvn %s\n' "${mvn_args[*]}"
    # Foreground, streaming: a build whose output is hidden is a build you
    # cannot diagnose.
    (cd "$PROJECT_ROOT" && mvn "${mvn_args[@]}")
fi

# --- run -------------------------------------------------------------------
# From the project root: config/ and ./output are both resolved relative to
# the working directory.
cd "$PROJECT_ROOT"

# UseParallelGC, not the G1 default: a short batch of short-lived objects with
# no latency target is exactly what a throughput collector is for. Comment it
# out to compare -- the difference is measurable at a million events.
JVM_ARGS=(
    -XX:+UseParallelGC
    -Xms256m
    -Xmx1g
    -XX:+ExitOnOutOfMemoryError
)

printf 'running  : java %s -jar target/%s --pipeline.env=%s %s\n\n' \
    "${JVM_ARGS[*]}" "$JAR_NAME" "$PROFILE" "${APP_ARGS[*]:-}"

# `set +e` around the run only: a non-zero exit here is data, not a script bug.
set +e
"$JAVA_BIN" "${JVM_ARGS[@]}" -jar "$JAR_PATH" "--pipeline.env=$PROFILE" "${APP_ARGS[@]:-}"
exit_code=$?
set -e

case "$exit_code" in
    0)   printf '\nOK (exit 0)\n' ;;
    2)   printf '\nconfiguration error (exit 2) -- nothing was started\n' ;;
    130) printf '\ninterrupted after a clean drain (exit 130)\n' ;;
    *)   printf '\nFAILED (exit %s)\n' "$exit_code" ;;
esac

# Propagate, never translate. A wrapper that always exits 0 makes every
# scheduled run look successful.
exit "$exit_code"
