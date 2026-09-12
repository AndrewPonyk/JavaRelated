param(
    [ValidateRange(1, 1000000)]
    [int]$TraderCount = 10000,
    [string]$Output = "target/stock-simulator.jfr"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $projectRoot "target/stock-trading-simulator-1.0.0-SNAPSHOT.jar"
$recording = Join-Path $projectRoot $Output

if (-not (Test-Path -LiteralPath $jar)) {
    throw "Runnable JAR not found. Run 'mvn verify' first."
}

$recordingDirectory = Split-Path -Parent $recording
if ($recordingDirectory) {
    New-Item -ItemType Directory -Path $recordingDirectory -Force | Out-Null
}

$env:SIM_TRADER_COUNT = $TraderCount
& java "-XX:StartFlightRecording=filename=$recording,settings=profile,dumponexit=true" -jar $jar
if ($LASTEXITCODE -ne 0) {
    throw "Simulator exited with code $LASTEXITCODE."
}
Write-Output "Flight recording written to $recording"
