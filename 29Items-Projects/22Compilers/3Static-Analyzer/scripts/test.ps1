$ErrorActionPreference = "Stop"

function Invoke-Checked {
  param([scriptblock]$Command)
  & $Command
  if ($LASTEXITCODE -ne 0) {
    throw "Command failed with exit code $LASTEXITCODE"
  }
}

Push-Location frontend
try {
  Invoke-Checked { npm test }
  Invoke-Checked { npm run build }
} finally {
  Pop-Location
}

$env:PYTHONPATH = "backend"
New-Item -ItemType Directory -Force -Path ".coverage-data" | Out-Null
$env:COVERAGE_FILE = ".coverage-data\.coverage-$PID"
Invoke-Checked { .\.venv\Scripts\python.exe -m coverage run -m pytest backend\tests }
Invoke-Checked { .\.venv\Scripts\python.exe -m coverage report }
Remove-Item Env:PYTHONPATH -ErrorAction SilentlyContinue
Remove-Item Env:COVERAGE_FILE -ErrorAction SilentlyContinue
