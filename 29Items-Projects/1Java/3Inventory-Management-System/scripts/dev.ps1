param(
    [ValidateSet('up', 'down', 'status', 'logs')]
    [string]$Action = 'up'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    switch ($Action) {
        'up' { docker compose up --build -d }
        'down' { docker compose down }
        'status' { docker compose ps }
        'logs' { docker compose logs --tail=100 -f }
    }
} finally {
    Pop-Location
}

