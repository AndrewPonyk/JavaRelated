param(
    [string]$BackendUrl = 'http://localhost:8080',
    [string]$ForecastUrl = 'http://localhost:8000',
    [string]$FrontendUrl = 'http://localhost:5173'
)

$ErrorActionPreference = 'Stop'
$checks = @(
    @{ Name = 'Backend'; Url = "$BackendUrl/actuator/health" },
    @{ Name = 'Forecast'; Url = "$ForecastUrl/health/ready" },
    @{ Name = 'Frontend'; Url = $FrontendUrl }
)

foreach ($check in $checks) {
    $response = Invoke-WebRequest -UseBasicParsing -Uri $check.Url -TimeoutSec 10
    if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 400) {
        throw "$($check.Name) smoke check failed with HTTP $($response.StatusCode)."
    }
    Write-Host "$($check.Name): OK"
}
