# Development environment setup script for E-Commerce Platform (PowerShell)

Write-Host "Setting up E-Commerce Platform development environment..." -ForegroundColor Cyan

# Check for required tools
$requiredTools = @("docker", "python", "node")
foreach ($tool in $requiredTools) {
    if (!(Get-Command $tool -ErrorAction SilentlyContinue)) {
        Write-Host "$tool is required but not installed. Aborting." -ForegroundColor Red
        exit 1
    }
}

# Create .env file if it doesn't exist
if (!(Test-Path ".env")) {
    Write-Host "Creating .env file from template..."
    Copy-Item ".env.example" ".env"
    Write-Host "Please update .env with your configuration values." -ForegroundColor Yellow
}

# Start infrastructure services
Write-Host "Starting infrastructure services (PostgreSQL, Redis, Elasticsearch)..." -ForegroundColor Cyan
docker-compose -f docker-compose.dev.yml up -d db redis elasticsearch

# Wait for services to be healthy
Write-Host "Waiting for services to be ready..."
Start-Sleep -Seconds 10

# Set up backend
Write-Host "Setting up backend..." -ForegroundColor Cyan
Push-Location backend

# Create virtual environment
if (!(Test-Path "venv")) {
    python -m venv venv
}

# Activate virtual environment and install dependencies
& .\venv\Scripts\Activate.ps1
pip install --upgrade pip
pip install -r requirements-dev.txt

# Run database migrations
Write-Host "Running database migrations..."
python manage.py migrate

# Ask about superuser
$createSuperuser = Read-Host "Would you like to create a superuser? (y/n)"
if ($createSuperuser -eq "y") {
    python manage.py createsuperuser
}

deactivate
Pop-Location

# Set up frontend
Write-Host "Setting up frontend..." -ForegroundColor Cyan
Push-Location frontend
npm install
Pop-Location

# Install pre-commit hooks
Write-Host "Installing pre-commit hooks..."
pip install pre-commit
pre-commit install

Write-Host ""
Write-Host "Setup complete!" -ForegroundColor Green
Write-Host ""
Write-Host "To start the development servers:"
Write-Host "  Backend:  cd backend; .\venv\Scripts\Activate.ps1; python manage.py runserver"
Write-Host "  Frontend: cd frontend; npm run dev"
Write-Host "  All:      docker-compose -f docker-compose.dev.yml up"
Write-Host ""
Write-Host "Access points:"
Write-Host "  Frontend:      http://localhost:3000"
Write-Host "  Backend API:   http://localhost:8000/api/v1/"
Write-Host "  Django Admin:  http://localhost:8000/admin/"
