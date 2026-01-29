# Test runner script for E-Commerce Platform (PowerShell)

param(
    [switch]$Backend,
    [switch]$Frontend,
    [switch]$Coverage
)

function Run-BackendTests {
    Write-Host "Running backend tests..." -ForegroundColor Cyan
    Push-Location backend
    & .\venv\Scripts\Activate.ps1

    if ($Coverage) {
        pytest --cov=apps --cov-report=html --cov-report=term
    } else {
        pytest
    }

    deactivate
    Pop-Location
}

function Run-FrontendTests {
    Write-Host "Running frontend tests..." -ForegroundColor Cyan
    Push-Location frontend

    if ($Coverage) {
        npm run test:coverage
    } else {
        npm run test
    }

    Pop-Location
}

# Run tests based on flags
if ($Backend) {
    Run-BackendTests
} elseif ($Frontend) {
    Run-FrontendTests
} else {
    Run-BackendTests
    Run-FrontendTests
}

Write-Host ""
Write-Host "All tests completed!" -ForegroundColor Green
