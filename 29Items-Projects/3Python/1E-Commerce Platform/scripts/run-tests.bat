@echo off
REM Test runner script for E-Commerce Platform (Windows)

setlocal enabledelayedexpansion

set BACKEND_ONLY=false
set FRONTEND_ONLY=false
set COVERAGE=false

REM Parse arguments
:parse_args
if "%~1"=="" goto :run_tests
if /i "%~1"=="--backend" set BACKEND_ONLY=true
if /i "%~1"=="--frontend" set FRONTEND_ONLY=true
if /i "%~1"=="--coverage" set COVERAGE=true
shift
goto :parse_args

:run_tests

if "%BACKEND_ONLY%"=="true" (
    call :run_backend_tests
    goto :done
)

if "%FRONTEND_ONLY%"=="true" (
    call :run_frontend_tests
    goto :done
)

call :run_backend_tests
call :run_frontend_tests
goto :done

:run_backend_tests
echo Running backend tests...
cd backend
call venv\Scripts\activate.bat

if "%COVERAGE%"=="true" (
    pytest --cov=apps --cov-report=html --cov-report=term
) else (
    pytest
)

call venv\Scripts\deactivate.bat
cd ..
exit /b 0

:run_frontend_tests
echo Running frontend tests...
cd frontend

if "%COVERAGE%"=="true" (
    call npm run test:coverage
) else (
    call npm run test
)

cd ..
exit /b 0

:done
echo.
echo All tests completed!
pause
