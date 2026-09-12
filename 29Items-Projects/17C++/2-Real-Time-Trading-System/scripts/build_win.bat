@echo off
REM Windows dev build helper: loads VS2022 env + bundled cmake/ninja, runs args.
REM   scripts\build_win.bat cmake -S . -B build -G Ninja
REM   scripts\build_win.bat cmake --build build
REM   scripts\build_win.bat ctest --test-dir build --output-on-failure
setlocal
set "VS=C:\Programs\Microsoft Visual Studio\2022\Professional"
call "%VS%\VC\Auxiliary\Build\vcvars64.bat" >nul 2>&1
set "PATH=%VS%\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin;%VS%\Common7\IDE\CommonExtensions\Microsoft\CMake\Ninja;%PATH%"
%*
endlocal
