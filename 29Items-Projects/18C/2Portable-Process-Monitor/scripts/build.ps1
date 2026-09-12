<#
.SYNOPSIS
    Configure, build, and test ppmon.
.EXAMPLE
    ./scripts/build.ps1 -Config Release -Test
#>
[CmdletBinding()]
param(
    [ValidateSet('Debug', 'Release', 'RelWithDebInfo')]
    [string]$Config = 'Release',
    [string]$BuildDir = 'build',
    [switch]$Test,
    [switch]$Package
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot

cmake -S $repoRoot -B (Join-Path $repoRoot $BuildDir) -DCMAKE_BUILD_TYPE=$Config
if ($LASTEXITCODE -ne 0) { throw "configure failed" }

cmake --build (Join-Path $repoRoot $BuildDir) --config $Config --parallel
if ($LASTEXITCODE -ne 0) { throw "build failed" }

if ($Test) {
    ctest --test-dir (Join-Path $repoRoot $BuildDir) -C $Config --output-on-failure
    if ($LASTEXITCODE -ne 0) { throw "tests failed" }
}

if ($Package) {
    cmake --build (Join-Path $repoRoot $BuildDir) --config $Config --target package
}

Write-Host "Done." -ForegroundColor Green
