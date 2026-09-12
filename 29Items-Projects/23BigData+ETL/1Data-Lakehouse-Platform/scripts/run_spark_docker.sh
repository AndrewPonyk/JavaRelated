#!/usr/bin/env bash
# Run the Spark lane in the Linux runner image (tests by default, or the
# end-to-end smoke against the compose stack):
#   scripts/run_spark_docker.sh tests
#   scripts/run_spark_docker.sh smoke
# The ivy cache persists in a named volume so connector jars download once.
set -euo pipefail

# Git Bash on Windows rewrites /container/paths in arguments; disable that and
# use a Windows-style host path for the bind mount.
export MSYS_NO_PATHCONV=1

cd "$(dirname "$0")/.."
REPO_DIR="$(pwd -W 2>/dev/null || pwd)"
MODE="${1:-tests}"

docker build -q -f infra/docker/spark/Dockerfile -t lakehouse-spark . >/dev/null
echo "==> lakehouse-spark image ready"

COMMON_ARGS=(
  --rm
  -v "$REPO_DIR:/work"
  -v lakehouse-ivy:/root/.ivy2
  -e PYSPARK_PYTHON=python
)

case "$MODE" in
  tests)
    docker run "${COMMON_ARGS[@]}" lakehouse-spark \
      python -m pytest data-platform/tests -m spark -p no:cacheprovider "${@:2}"
    ;;
  smoke)
    # Joins the compose network: real Kafka + Catalog API, file:// lake storage.
    NETWORK="$(docker network ls --format '{{.Name}}' | grep -iE '(lakehouse|1data).*_default' | head -1)"
    docker run "${COMMON_ARGS[@]}" \
      --network "${NETWORK:?compose network not found - run 'docker compose up -d' first}" \
      -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
      -e CATALOG_API_URL=http://api:8000 \
      lakehouse-spark \
      python scripts/smoke_e2e.py "${@:2}"
    ;;
  *)
    echo "usage: $0 [tests|smoke]" >&2
    exit 2
    ;;
esac
