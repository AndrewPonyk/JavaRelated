[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'High')]
param()

$ErrorActionPreference = 'Stop'

if (-not (Test-Path '.env')) {
  throw 'Missing .env. Copy .env.example to .env and replace placeholder values first.'
}

$envText = Get-Content '.env'
$placeholderLines = $envText | Where-Object {
  $_ -match '^[A-Za-z_][A-Za-z0-9_]*=' -and ($_ -match '=replace_with' -or $_ -match '=your_generated')
}

if ($placeholderLines.Count -gt 0) {
  throw "Replace placeholder values in .env before resetting the local database: $($placeholderLines -join ', ')"
}

Write-Warning 'This removes local Docker Compose volumes, including the Postgres database and Redis data.'
Write-Warning 'Use this only for local development when the stored Postgres password no longer matches .env.'

if ($PSCmdlet.ShouldProcess('Docker Compose local volumes', 'docker compose down --volumes; docker compose up --build -d')) {
  docker compose down --volumes
  docker compose up --build -d
}
