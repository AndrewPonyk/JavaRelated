$ErrorActionPreference = "Stop"

Write-Host "Running Python computational geometry demo..."
$env:PYTHONPATH = "src/python"
python -m geometry.cli.main

Write-Host ""
Write-Host "Running Python tests..."
pytest

Write-Host ""
Write-Host "Running Java tests..."
Push-Location "src/java"
try {
    mvn test
    Write-Host ""
    Write-Host "Running Java computational geometry demo..."
    java -cp "target\classes" org.computationalgeometry.cli.Main --input "..\..\data\fixtures\default_points.csv"
}
finally {
    Pop-Location
}
