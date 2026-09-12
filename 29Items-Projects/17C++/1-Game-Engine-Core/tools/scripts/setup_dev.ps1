<#
.SYNOPSIS
    One-shot developer environment setup for Game Engine Core (Windows).
.DESCRIPTION
    Verifies toolchain prerequisites, bootstraps vcpkg if needed, and configures the
    'dev' CMake preset. Run from a Developer PowerShell so MSVC is on PATH.
.EXAMPLE
    ./tools/scripts/setup_dev.ps1
#>
[CmdletBinding()]
param(
    [string]$Preset = "dev"
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path "$PSScriptRoot/../..").Path
Write-Host "Repo root: $RepoRoot"

function Test-Tool($name) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
        Write-Warning "'$name' not found on PATH."
        return $false
    }
    Write-Host "  found: $name"
    return $true
}

Write-Host "Checking prerequisites..."
$ok = $true
$ok = (Test-Tool "cmake")  -and $ok
$ok = (Test-Tool "ninja")  -and $ok
if (-not (Test-Tool "git")) { $ok = $false }
if (-not $ok) { Write-Warning "Install missing tools (CMake >= 3.24, Ninja, Git) and re-run." }

# Bootstrap vcpkg if VCPKG_ROOT is unset.
if (-not $env:VCPKG_ROOT) {
    $vcpkgDir = Join-Path $RepoRoot "external/vcpkg"
    if (-not (Test-Path $vcpkgDir)) {
        Write-Host "Cloning vcpkg into $vcpkgDir ..."
        git clone --depth 1 https://github.com/microsoft/vcpkg $vcpkgDir
        & (Join-Path $vcpkgDir "bootstrap-vcpkg.bat") -disableMetrics
    }
    $env:VCPKG_ROOT = $vcpkgDir
    Write-Host "VCPKG_ROOT set to $env:VCPKG_ROOT"
}

if (-not $env:VULKAN_SDK) {
    Write-Warning "VULKAN_SDK is not set. Install the Vulkan SDK for the Vulkan backend + glslc."
}

Write-Host "Configuring CMake preset '$Preset'..."
& cmake --preset $Preset

Write-Host "Done. Build with:  cmake --build --preset $Preset"
