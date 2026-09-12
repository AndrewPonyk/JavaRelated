param(
    [string]$DatabasePath = "build/jsengine.sqlite"
)

$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Force (Split-Path -Parent $DatabasePath) | Out-Null
sqlite3 $DatabasePath ".read migrations/001_initial_schema.sql"
Write-Host "Applied migrations to $DatabasePath"
