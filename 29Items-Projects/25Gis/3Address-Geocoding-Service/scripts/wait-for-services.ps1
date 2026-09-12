param(
    [string]$BackendUrl = "http://localhost:8000/api/v1/health",
    [int]$TimeoutSeconds = 60
)

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)

while ((Get-Date) -lt $deadline) {
    try {
        $response = Invoke-WebRequest -Uri $BackendUrl -UseBasicParsing -TimeoutSec 5
        if ($response.StatusCode -eq 200) {
            Write-Output "Backend is ready."
            exit 0
        }
    }
    catch {
        Start-Sleep -Seconds 2
    }
}

Write-Error "Backend did not become ready within $TimeoutSeconds seconds."
exit 1
