param(
  [string]$EnvFile = ".env"
)

if (-not (Test-Path $EnvFile)) {
  Write-Error "Missing $EnvFile. Copy .env.example to .env and fill in local values."
  exit 1
}

docker compose --env-file $EnvFile up --build
