@echo off
title RTS Dev Shell
set "VS=C:\Programs\Microsoft Visual Studio\2022\Professional"
call "%VS%\VC\Auxiliary\Build\vcvars64.bat" >nul 2>&1
set "PATH=%VS%\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin;%VS%\Common7\IDE\CommonExtensions\Microsoft\CMake\Ninja;%PATH%"
cd /d "C:\mygit\JavaRelated\29Items-Projects\17C++\2-Real-Time-Trading-System"
echo.
echo  ============================================================
echo   RTS dev shell ready: cl, cmake, ctest, ninja are on PATH.
echo   Project: %CD%
echo.
echo   Run tests:   ctest --test-dir build --output-on-failure
echo   Rebuild:     cmake --build build
echo   Run engine:  build\trading_engine.exe config\trading_engine.yaml
echo  ============================================================
echo.
cmd /k
