$ErrorActionPreference = "Stop"

Write-Host "Starting backend at http://localhost:8000"
Start-Process powershell -WindowStyle Hidden -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\..'; `$env:PYTHONPATH='backend'; .\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --app-dir backend"

Write-Host "Starting frontend at http://localhost:5173"
Push-Location "$PSScriptRoot\..\frontend"
try {
  npm run dev
} finally {
  Pop-Location
}
