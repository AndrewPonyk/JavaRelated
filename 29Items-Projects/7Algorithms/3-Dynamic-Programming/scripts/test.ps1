$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$env:PYTHONPATH = Join-Path $root "python"
pytest
Push-Location (Join-Path $root "java")
try {
    mvn test
} finally {
    Pop-Location
}
