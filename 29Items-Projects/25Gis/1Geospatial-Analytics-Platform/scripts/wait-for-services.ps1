param(
    [string]$BackendUrl = "http://localhost:8000/health",
    [int]$TimeoutSeconds = 60
)

$ErrorActionPreference = "Stop"
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)

while ((Get-Date) -lt $deadline) {
    try {
        $response = Invoke-WebRequest -Uri $BackendUrl -UseBasicParsing -TimeoutSec 3
        if ($response.StatusCode -eq 200) {
            Write-Host "Backend is ready: $BackendUrl"
            exit 0
        }
    } catch {
        Start-Sleep -Seconds 2
    }
}

throw "Timed out waiting for backend: $BackendUrl"
