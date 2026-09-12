param(
  [string]$Direction = "up"
)

if (-not $env:DATABASE_URL) {
  Write-Error "DATABASE_URL is required"
  exit 1
}

migrate -path migrations -database $env:DATABASE_URL $Direction

