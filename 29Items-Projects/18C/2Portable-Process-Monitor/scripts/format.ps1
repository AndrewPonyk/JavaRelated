<#
.SYNOPSIS
    Run clang-format over all C sources and headers.
.PARAMETER Check
    Only verify formatting (CI mode); do not modify files.
#>
[CmdletBinding()]
param([switch]$Check)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$files = Get-ChildItem -Path (Join-Path $repoRoot 'src'),
                             (Join-Path $repoRoot 'include'),
                             (Join-Path $repoRoot 'tests') `
                       -Recurse -Include *.c, *.h

if (-not $files) { Write-Host "No source files found."; return }

if ($Check) {
    clang-format --dry-run --Werror $files.FullName
} else {
    clang-format -i $files.FullName
    Write-Host "Formatted $($files.Count) files." -ForegroundColor Green
}
