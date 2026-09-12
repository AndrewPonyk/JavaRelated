$ErrorActionPreference = "Stop"

.\.venv\Scripts\python.exe -m ruff check --no-cache backend
Push-Location frontend
try {
  npm run lint
} finally {
  Pop-Location
}
