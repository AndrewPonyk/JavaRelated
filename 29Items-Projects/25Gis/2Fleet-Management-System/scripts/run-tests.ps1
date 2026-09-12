$ErrorActionPreference = "Stop"

Push-Location "$PSScriptRoot\..\backend"
pytest
$coverageFile = Join-Path $env:TEMP "fleet-coverage.sqlite"
if (Test-Path $coverageFile) {
    Remove-Item -LiteralPath $coverageFile -Force
}
coverage run --data-file=$coverageFile -m pytest
coverage report --data-file=$coverageFile --fail-under=70
Pop-Location

Push-Location "$PSScriptRoot\..\frontend"
npm test
Pop-Location
