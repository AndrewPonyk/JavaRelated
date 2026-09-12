<#
.SYNOPSIS
  Configure, build, and test MiniDB on Windows (MSVC).

.EXAMPLE
  ./scripts/build.ps1                  # Release
  ./scripts/build.ps1 -BuildType Debug # Debug

.NOTES
  If cmake/cl are not already on PATH, run this from a "Developer PowerShell for
  VS 2022", or let the script try to locate and import the VS dev environment.
#>
param(
  [ValidateSet('Debug', 'Release')]
  [string]$BuildType = 'Release'
)

$ErrorActionPreference = 'Stop'
$buildDir = if ($BuildType -eq 'Debug') { 'build-debug' } else { 'build' }

# Try to enter the VS developer environment if cmake isn't visible yet.
if (-not (Get-Command cmake -ErrorAction SilentlyContinue)) {
  $vswhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
  if (Test-Path $vswhere) {
    $vsPath = & $vswhere -latest -products * -requires Microsoft.Component.MSBuild -property installationPath
    $devShell = Join-Path $vsPath 'Common7\Tools\Microsoft.VisualStudio.DevShell.dll'
    if (Test-Path $devShell) {
      Import-Module $devShell
      Enter-VsDevShell -VsInstallPath $vsPath -SkipAutomaticLocation -DevCmdArguments '-arch=x64'
    }
  }
}

cmake -S . -B $buildDir -DCMAKE_BUILD_TYPE=$BuildType
if ($LASTEXITCODE -ne 0) { throw "configure failed" }

cmake --build $buildDir --config $BuildType --parallel
if ($LASTEXITCODE -ne 0) { throw "build failed" }

ctest --test-dir $buildDir -C $BuildType --output-on-failure
if ($LASTEXITCODE -ne 0) { throw "tests failed" }

Write-Host "Done. CLI at $buildDir\$BuildType\minidb.exe (or $buildDir\minidb.exe)"
