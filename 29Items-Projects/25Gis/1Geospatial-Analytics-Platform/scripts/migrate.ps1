param(
    [string]$Container = "1geospatial-analytics-platform-postgis-1",
    [string]$Database = "geospatial",
    [string]$User = "gis"
)

$ErrorActionPreference = "Stop"

Get-ChildItem -Path database/migrations -Filter *.sql | Sort-Object Name | ForEach-Object {
    Write-Host "Applying migration $($_.Name)"
    Get-Content -Raw $_.FullName | docker exec -i $Container psql -U $User -d $Database
}
