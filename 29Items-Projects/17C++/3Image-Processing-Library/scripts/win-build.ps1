# scripts/win-build.ps1
# Build + test imgproc on Windows using the locally-installed Visual Studio 2022
# toolchain. Auto-discovers VS, the MSVC compiler, the Windows SDK, and the
# CMake/Ninja bundled with VS, so it works even when none are on PATH.
#
#   powershell -ExecutionPolicy Bypass -File scripts\win-build.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\win-build.ps1 -Config Debug -NoTest
[CmdletBinding()]
param(
    [string]$BuildDir = "build",
    [ValidateSet("Release", "Debug", "RelWithDebInfo")]
    [string]$Config = "Release",
    [switch]$NoTest
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

# 1. Locate Visual Studio via vswhere.
$vswhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
if (-not (Test-Path $vswhere)) { throw "vswhere.exe not found at $vswhere" }
$vsPath = & $vswhere -latest -products * -property installationPath
if (-not $vsPath) { throw "No Visual Studio installation found." }
$vcvars = Join-Path $vsPath "VC\Auxiliary\Build\vcvars64.bat"

# 2. Newest MSVC host-x64 toolset (cl.exe) and Windows SDK bin (rc.exe / mt.exe).
$clExe = Get-ChildItem "$vsPath\VC\Tools\MSVC\*\bin\Hostx64\x64\cl.exe" -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending | Select-Object -First 1
if (-not $clExe) { throw "cl.exe not found under $vsPath\VC\Tools\MSVC" }
$clDir = Split-Path $clExe.FullName -Parent

$rcExe = Get-ChildItem "${env:ProgramFiles(x86)}\Windows Kits\10\bin\*\x64\rc.exe" -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending | Select-Object -First 1
$sdkBin = if ($rcExe) { Split-Path $rcExe.FullName -Parent } else { "" }

# 3. CMake + Ninja shipped with Visual Studio.
$cmakeDir = "$vsPath\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin"
$ninjaDir = "$vsPath\Common7\IDE\CommonExtensions\Microsoft\CMake\Ninja"
$installer = Split-Path $vswhere -Parent

Write-Host "VS      : $vsPath"
Write-Host "MSVC    : $clDir"
Write-Host "WinSDK  : $sdkBin"
Write-Host "CMake   : $cmakeDir`n"

# 4. Compose one cmd line: vcvars (sets INCLUDE/LIB) + explicit tool dirs, then
#    configure -> build -> test. The installer dir is prepended so vcvars can
#    find vswhere; cl/SDK are added explicitly because this VS layout does not
#    add them to PATH on its own.
$steps = "cmake -S `"$root`" -B `"$root\$BuildDir`" -G Ninja -DCMAKE_BUILD_TYPE=$Config" +
         " && cmake --build `"$root\$BuildDir`" -j"
if (-not $NoTest) {
    $steps += " && ctest --test-dir `"$root\$BuildDir`" --output-on-failure"
}
$path = "$clDir;$sdkBin;$cmakeDir;$ninjaDir;%PATH%"
$full = "set `"PATH=$installer;%PATH%`" && call `"$vcvars`" >nul && set `"PATH=$path`" && $steps"

cmd /c $full
exit $LASTEXITCODE
