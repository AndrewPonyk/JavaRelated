$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $root "java")
try {
    mvn -q compile
    java -cp "target/classes" com.example.dp.App
} finally {
    Pop-Location
}
