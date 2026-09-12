param(
    [string]$Container = "1geospatial-analytics-platform-postgis-1",
    [string]$Database = "geospatial",
    [string]$User = "gis",
    [string]$Output = "artifacts/postgis-backup.dump"
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path (Split-Path $Output) | Out-Null
docker exec $Container pg_dump -U $User -d $Database -Fc | Set-Content -Encoding Byte -Path $Output
Write-Host "Backup written to $Output"
