# PowerShell script to start development environment
# Run: .\start-dev.ps1

Write-Host "E-Commerce Platform - Development Environment" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""

# Check if Docker is running
try {
    docker info | Out-Null
} catch {
    Write-Host "Error: Docker is not running. Please start Docker Desktop first." -ForegroundColor Red
    exit 1
}

# Copy .env.example to .env if not exists
if (-not (Test-Path ".env")) {
    Write-Host "Creating .env file from .env.example..." -ForegroundColor Yellow
    Copy-Item ".env.example" ".env"
}

# Start the development environment
Write-Host ""
Write-Host "Starting services (PostgreSQL, Redis, Elasticsearch, Backend, Frontend)..." -ForegroundColor Green
Write-Host "This may take a few minutes on first run." -ForegroundColor Yellow
Write-Host ""
Write-Host "Once started, access the application at:" -ForegroundColor Cyan
Write-Host "  Frontend: http://localhost:3000" -ForegroundColor White
Write-Host "  Backend API: http://localhost:8000/api/v1/" -ForegroundColor White
Write-Host "  Admin Panel: http://localhost:8000/admin/" -ForegroundColor White
Write-Host "  (Login: admin@example.com / admin123)" -ForegroundColor White
Write-Host ""
Write-Host "Press Ctrl+C to stop all services." -ForegroundColor Yellow
Write-Host ""

docker-compose -f docker-compose.dev.yml up --build
