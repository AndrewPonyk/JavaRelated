$ErrorActionPreference = "Stop"

$env:PYTHONPATH = "src/python"
$env:RUFF_NO_CACHE = "true"
ruff check .
python -m pytest

if (Get-Command mvn -ErrorAction SilentlyContinue) {
    mvn test
} else {
    Write-Host "Maven not found; compiling Java sources with javac only."
    $buildDir = "out/java"
    New-Item -ItemType Directory -Force -Path $buildDir | Out-Null
    $sources = Get-ChildItem -Path "src/java" -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
    javac -d $buildDir $sources
}
