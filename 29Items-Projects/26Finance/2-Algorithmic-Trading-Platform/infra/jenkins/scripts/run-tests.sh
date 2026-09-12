#!/usr/bin/env bash
# Helper invoked by the Jenkins "Unit Tests" stage (Python lane).
# Kept here so the pipeline stays declarative and the logic is locally runnable.
set -euo pipefail

echo "==> Installing shared lib + service dev extras"
pip install -e shared
pip install -e "services/api-gateway[dev]" -e "services/strategy-engine[dev]"

echo "==> Running ruff / mypy"
ruff check shared services
mypy shared services

echo "==> Running pytest with coverage"
pytest --cov --cov-report=xml --cov-report=term-missing --junitxml=reports/pytest.xml

echo "==> Done."
