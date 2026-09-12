$ErrorActionPreference = "Stop"

$files = Get-ChildItem -Path include,src,tests -Recurse -Include *.h,*.cpp
if ($files.Count -eq 0) {
    Write-Host "No C++ files found."
    exit 0
}

clang-format -i $files.FullName
