@echo off
REM ============================================================================
REM  Build & test the Memory Allocator natively on Windows with MSVC.
REM
REM  Usage:  scripts\build-windows.bat [Debug|Release]   (default: Debug)
REM
REM  Auto-loads the Visual Studio x64 developer environment and the CMake/Ninja
REM  bundled with VS, so it works from a plain cmd.exe (no Developer Prompt
REM  needed). Override detection by setting VSINSTALL=<path-to-VS> beforehand.
REM ============================================================================
setlocal

set "CONFIG=%~1"
if "%CONFIG%"=="" set "CONFIG=Debug"

REM --- 1. Ensure the MSVC compiler (cl) is available -------------------------
where cl >nul 2>&1
if %errorlevel%==0 goto have_compiler

if defined VSINSTALL goto have_vsinstall

REM Try vswhere (standard installer location, even when VS itself is elsewhere).
set "VSWHERE=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
if not exist "%VSWHERE%" set "VSWHERE=%ProgramFiles%\Microsoft Visual Studio\Installer\vswhere.exe"
if exist "%VSWHERE%" for /f "usebackq tokens=*" %%i in (`"%VSWHERE%" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do set "VSINSTALL=%%i"

REM Fallback: this machine's known non-default install path.
if not defined VSINSTALL if exist "C:\Programs\Microsoft Visual Studio\2022\Professional" set "VSINSTALL=C:\Programs\Microsoft Visual Studio\2022\Professional"

:have_vsinstall
if not defined VSINSTALL (
  echo ERROR: Visual Studio with C++ tools not found.
  echo        Set VSINSTALL to your VS install path or run from a Developer Command Prompt.
  exit /b 1
)
echo Using Visual Studio at: %VSINSTALL%
call "%VSINSTALL%\VC\Auxiliary\Build\vcvars64.bat" >nul
if errorlevel 1 ( echo ERROR: failed to load vcvars64.bat & exit /b 1 )

:have_compiler

REM --- 2. Make sure cmake and ninja are on PATH (bundled with VS) ------------
where cmake >nul 2>&1
if errorlevel 1 if defined VSINSTALL set "PATH=%VSINSTALL%\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin;%PATH%"
where ninja >nul 2>&1
if errorlevel 1 if defined VSINSTALL set "PATH=%VSINSTALL%\Common7\IDE\CommonExtensions\Microsoft\CMake\Ninja;%PATH%"

where cmake >nul 2>&1
if errorlevel 1 ( echo ERROR: cmake not found on PATH. & exit /b 1 )

REM --- 3. Configure, build, test --------------------------------------------
cd /d "%~dp0.."
set "BUILD_DIR=build-win"

echo.
echo === Configuring (%CONFIG%, MEM_DEBUG=ON) ===
cmake -S . -B "%BUILD_DIR%" -G Ninja -DCMAKE_BUILD_TYPE=%CONFIG% -DMEMALLOC_DEBUG=ON
if errorlevel 1 ( echo CONFIGURE FAILED & exit /b 1 )

echo.
echo === Building ===
cmake --build "%BUILD_DIR%"
if errorlevel 1 ( echo BUILD FAILED & exit /b 1 )

echo.
echo === Running tests (ctest) ===
ctest --test-dir "%BUILD_DIR%" --output-on-failure
if errorlevel 1 ( echo TESTS FAILED & exit /b 1 )

echo.
echo === SUCCESS ===
echo Run the demo:   %BUILD_DIR%\bin\demo.exe best
echo Run benchmarks: %BUILD_DIR%\bin\bench_strategies.exe
endlocal
