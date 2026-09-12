#!/usr/bin/env bash
# Linux/macOS dev bootstrap. Requires: Python 3.10+, g++/clang++ (C++17), CMake.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "==> Creating virtual environment"
[ -d .venv ] || python3 -m venv .venv
# shellcheck disable=SC1091
source .venv/bin/activate

echo "==> Installing quantfinlib (editable, builds the C++ extension)"
pip install --upgrade pip
pip install -v -e ".[api,dev]"

echo "==> Installing pre-commit hooks"
pre-commit install

echo "==> Copying .env template"
[ -f .env ] || cp .env.example .env

echo "==> Running the test suite"
pytest tests/python -q

cat <<'EOF'

Done. Next steps:
  pytest tests -q                                # all tests
  uvicorn app.main:app --reload --app-dir api    # run the API -> http://localhost:8000
  python examples/quickstart.py                  # library tour
EOF
