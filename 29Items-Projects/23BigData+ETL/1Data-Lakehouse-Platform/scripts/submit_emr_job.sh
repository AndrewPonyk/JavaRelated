#!/usr/bin/env bash
# Submit a lakehouse job to EMR Serverless (ad-hoc runs and debugging; Airflow
# uses EmrServerlessStartJobRunOperator with identical driver settings).
#
# Usage:
#   EMR_APPLICATION_ID=... EMR_JOB_ROLE_ARN=... ARTIFACTS_BUCKET=... \
#     scripts/submit_emr_job.sh lakehouse.jobs.bronze_to_silver --run-date 2026-07-01
#
# Resolve the ids from terraform:
#   terraform -chdir=infra/terraform output emr_application_id
set -euo pipefail

MODULE="${1:?usage: submit_emr_job.sh <python.module> [args...]}"
shift

APPLICATION_ID="${EMR_APPLICATION_ID:?set EMR_APPLICATION_ID}"
EXECUTION_ROLE_ARN="${EMR_JOB_ROLE_ARN:?set EMR_JOB_ROLE_ARN}"
ARTIFACTS_BUCKET="${ARTIFACTS_BUCKET:?set ARTIFACTS_BUCKET}"
WHEEL_VERSION="${WHEEL_VERSION:-latest}"

ENTRY_ARGS="$(printf '"%s",' "$MODULE" "$@")"
ENTRY_ARGS="[${ENTRY_ARGS%,}]"

aws emr-serverless start-job-run \
  --application-id "$APPLICATION_ID" \
  --execution-role-arn "$EXECUTION_ROLE_ARN" \
  --name "${MODULE##*.}" \
  --job-driver "{
    \"sparkSubmit\": {
      \"entryPoint\": \"s3://$ARTIFACTS_BUCKET/entrypoints/run_module.py\",
      \"entryPointArguments\": $ENTRY_ARGS,
      \"sparkSubmitParameters\": \"--py-files s3://$ARTIFACTS_BUCKET/wheels/$WHEEL_VERSION/lakehouse.whl\"
    }
  }"
