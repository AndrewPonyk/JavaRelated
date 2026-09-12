#!/usr/bin/env bash
# Container entrypoint: apply migrations, optionally bootstrap a first model,
# then start the API. All steps honour the FRAUD_* environment variables.
set -euo pipefail

echo "[entrypoint] applying database migrations"
python -m alembic -c migrations/alembic.ini upgrade head

MODEL_DIR="${FRAUD_MODEL_DIR:-models}"
if [ "${FRAUD_BOOTSTRAP_MODEL:-false}" = "true" ] && [ ! -f "${MODEL_DIR}/registry.json" ]; then
  echo "[entrypoint] no model registered yet - training a bootstrap model"
  python -m fraud_detection.ml.train \
    --model-dir "${MODEL_DIR}" \
    --n-samples "${FRAUD_TRAINING_SAMPLES:-8000}"
fi

echo "[entrypoint] starting uvicorn"
exec uvicorn fraud_detection.main:app --host 0.0.0.0 --port 8000 "$@"
