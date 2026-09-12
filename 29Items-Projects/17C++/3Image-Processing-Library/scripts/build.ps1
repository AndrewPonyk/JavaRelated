# scripts/build.ps1 — Windows developer build helper.
# Usage:  ./scripts/build.ps1 [-Preset dev-cpu] [-Target all]
param(
    [string]$Preset = "default",
    [string]$Target = "all"
)

$ErrorActionPreference = "Stop"

# The default preset is dependency-light (pure C++ + vendored SQLite + fetched
# GoogleTest); no vcpkg required. Use the "full" preset for OpenCV/CUDA/Python.

Write-Host "==> Configuring (preset: $Preset)" -ForegroundColor Cyan
cmake --preset $Preset

Write-Host "==> Building" -ForegroundColor Cyan
cmake --build --preset $Preset

if ($Preset -eq "default" -or $Preset -eq "debug") {
    Write-Host "==> Testing" -ForegroundColor Cyan
    ctest --preset $Preset
}
