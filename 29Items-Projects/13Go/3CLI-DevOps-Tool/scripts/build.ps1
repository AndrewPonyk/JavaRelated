param(
  [string]$Output = "bin/devopsctl.exe"
)

$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Force -Path (Split-Path $Output) | Out-Null
go build -o $Output ./cmd/devopsctl
Write-Host "Built $Output"
