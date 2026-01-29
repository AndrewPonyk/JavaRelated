@echo off
echo E-Commerce Platform - Development Environment
echo =============================================
echo.

REM Check if Docker is running
docker info >nul 2>&1
if errorlevel 1 (
    echo Error: Docker is not running. Please start Docker Desktop first.
    pause
    exit /b 1
)

REM Copy .env.example to .env if not exists
if not exist ".env" (
    echo Creating .env file from .env.example...
    copy ".env.example" ".env"
)

echo.
echo Starting services (PostgreSQL, Redis, Elasticsearch, Backend, Frontend)...
echo This may take a few minutes on first run.
echo.
echo Once started, access the application at:
echo   Frontend: http://localhost:3000
echo   Backend API: http://localhost:8000/api/v1/
echo   Admin Panel: http://localhost:8000/admin/
echo   (Login: admin@example.com / admin123)
echo.
echo Press Ctrl+C to stop all services.
echo.

docker-compose -f docker-compose.dev.yml up --build
