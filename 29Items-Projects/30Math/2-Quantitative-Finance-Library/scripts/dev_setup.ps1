# Windows dev bootstrap. Requires: Python 3.10+, a C++17 compiler (VS Build Tools), CMake.
# Usage:  powershell -ExecutionPolicy Bypass -File scripts/dev_setup.ps1

$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

Write-Host "==> Creating virtual environment" -ForegroundColor Cyan
if (-not (Test-Path ".venv")) { python -m venv .venv }
& .\.venv\Scripts\Activate.ps1

Write-Host "==> Installing quantfinlib (editable, builds the C++ extension)" -ForegroundColor Cyan
pip install --upgrade pip
pip install -v -e ".[api,dev]"

Write-Host "==> Installing pre-commit hooks" -ForegroundColor Cyan
pre-commit install

Write-Host "==> Copying .env template" -ForegroundColor Cyan
if (-not (Test-Path ".env")) { Copy-Item ".env.example" ".env" }

Write-Host "==> Running the test suite" -ForegroundColor Cyan
pytest tests/python -q

Write-Host "`nDone. Next steps:" -ForegroundColor Green
Write-Host "  pytest tests -q                                # all tests"
Write-Host "  uvicorn app.main:app --reload --app-dir api    # run the API -> http://localhost:8000"
Write-Host "  python examples/quickstart.py                  # library tour"
