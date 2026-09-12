#!/usr/bin/env bash
# Submit a streaming job locally (or against any Spark master).
#
#   ./scripts/submit_spark_job.sh enrich_and_index
#   ./scripts/submit_spark_job.sh pattern_detection
#   ./scripts/submit_spark_job.sh anomaly_scoring
#
# Windows note: run inside WSL or the docker/spark.Dockerfile container — native
# Windows PySpark workers are unreliable (TECH-NOTES pitfall #11).
# EMR Serverless submission happens in .github/workflows/deploy.yml, not here.
set -euo pipefail

JOB="${1:?usage: submit_spark_job.sh <enrich_and_index|pattern_detection|anomaly_scoring>}"
shift || true

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Version MUST match pyspark in requirements-spark.txt (TECH-NOTES pitfall #4).
SPARK_PACKAGES="org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.1"

export PYTHONPATH="${REPO_ROOT}/src:${PYTHONPATH:-}"

exec spark-submit \
  --master "${LA_SPARK_MASTER:-local[*]}" \
  --packages "${SPARK_PACKAGES}" \
  --properties-file "${REPO_ROOT}/config/spark/spark-defaults.conf" \
  "${REPO_ROOT}/src/log_analytics/streaming/jobs/${JOB}.py" \
  "$@"
