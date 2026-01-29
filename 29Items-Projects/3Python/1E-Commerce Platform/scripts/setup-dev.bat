@echo off
REM Development environment setup script for E-Commerce Platform (Windows)

echo Setting up E-Commerce Platform development environment...

REM Check for required tools
where docker >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo Docker is required but not installed. Aborting.
    exit /b 1
)

where python >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo Python is required but not installed. Aborting.
    exit /b 1
)

where node >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo Node.js is required but not installed. Aborting.
    exit /b 1
)

REM Create .env file if it doesn't exist
if not exist .env (
    echo Creating .env file from template...
    copy .env.example .env
    echo Please update .env with your configuration values.
)

REM Start infrastructure services
echo Starting infrastructure services (PostgreSQL, Redis, Elasticsearch)...
docker-compose -f docker-compose.dev.yml up -d db redis elasticsearch

REM Wait for services to be healthy
echo Waiting for services to be ready...
timeout /t 10 /nobreak >nul

REM Set up backend
echo Setting up backend...
cd backend

REM Create virtual environment
if not exist venv (
    python -m venv venv
)

REM Activate virtual environment and install dependencies
call venv\Scripts\activate.bat
pip install --upgrade pip
pip install -r requirements-dev.txt

REM Run database migrations
echo Running database migrations...
python manage.py migrate

REM Ask about superuser
set /p create_superuser="Would you like to create a superuser? (y/n): "
if /i "%create_superuser%"=="y" (
    python manage.py createsuperuser
)

call venv\Scripts\deactivate.bat
cd ..

REM Set up frontend
echo Setting up frontend...
cd frontend
call npm install
cd ..

REM Install pre-commit hooks
echo Installing pre-commit hooks...
pip install pre-commit
pre-commit install

echo.
echo Setup complete!
echo.
echo To start the development servers:
echo   Backend:  cd backend ^&^& venv\Scripts\activate ^&^& python manage.py runserver
echo   Frontend: cd frontend ^&^& npm run dev
echo   All:      docker-compose -f docker-compose.dev.yml up
echo.
echo Access points:
echo   Frontend:      http://localhost:3000
echo   Backend API:   http://localhost:8000/api/v1/
echo   Django Admin:  http://localhost:8000/admin/

pause
