$ErrorActionPreference = "Stop"
Push-Location "$PSScriptRoot\..\python"
try {
    python -m greedy_algorithms
}
finally {
    Pop-Location
}
