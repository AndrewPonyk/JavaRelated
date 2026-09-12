<#
.SYNOPSIS
    Builds (if needed) and runs the Event Processing Pipeline on Windows.

.DESCRIPTION
    The primary entry point on this platform. It exists because three things
    have to be right before the pipeline starts, and getting any of them wrong
    produces a confusing failure:

      1. JAVA_HOME must point at a JDK 21+. Maven on this machine defaults to
         JDK 11, which cannot compile records-with-pattern-matching and fails
         with an "invalid target release" that says nothing about the cause.
      2. The jar has to exist. `mvn package` is skipped when the jar is newer
         than every source file, so the common case costs nothing.
      3. Arguments are passed through verbatim, so every --pipeline.* flag
         documented by `--help` works here too.

.PARAMETER Env
    Profile to load: dev | staging | prod. Selects
    config/application-<env>.properties. Default: dev.

.PARAMETER Build
    Force `mvn package` even when the jar looks current.

.PARAMETER SkipTests
    Pass -DskipTests to the build. For a fast iteration loop only -- never in
    CI, which is why this is a switch and not the default.

.EXAMPLE
    .\scripts\run.ps1
    A 20k-event dev run with the dashboard on.

.EXAMPLE
    .\scripts\run.ps1 -Env prod -- --pipeline.event.count=5000000
    Everything after `--` goes to the application untouched.

.EXAMPLE
    .\scripts\run.ps1 -- --help
    Prints every setting, its precedence chain and the exit codes.

.NOTES
    Exit codes are passed through unchanged, because they are the contract:
      0   success
      1   run failed, or events were lost
      2   invalid configuration -- nothing was started
      130 interrupted (Ctrl+C) after a clean drain
#>
[CmdletBinding()]
param(
    [ValidateSet('dev', 'staging', 'prod')]
    [string] $Env = 'dev',

    [switch] $Build,

    [switch] $SkipTests,

    # Everything not matched above. `--` separates our switches from the
    # application's flags, so -Env and --pipeline.env cannot be confused.
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $PipelineArgs = @()
)

# Stop on the first error. Without this a failed build is followed by a run
# against a stale jar, which is the single most misleading outcome available.
$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$jarName = 'event-processing-pipeline-1.0.0-SNAPSHOT.jar'
$jarPath = Join-Path $projectRoot "target\$jarName"

# --- JDK 21 --------------------------------------------------------------
# Checked before anything else: the failure message from a wrong JDK arrives
# minutes later, from Maven, and points at the wrong thing.
$requiredJdk = 'C:\Programs\jdk-21.0.2'
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
    if (Test-Path $requiredJdk) {
        Write-Host "JAVA_HOME not usable; using $requiredJdk" -ForegroundColor Yellow
        $env:JAVA_HOME = $requiredJdk
    } else {
        throw "Set JAVA_HOME to a JDK 21 or newer install (looked for $requiredJdk)."
    }
}
$javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'

# Fail loudly on JDK < 21 rather than at the first sealed-interface switch.
$versionLine = & $javaExe -version 2>&1 | Select-Object -First 1
if ($versionLine -match 'version "(\d+)') {
    $major = [int] $Matches[1]
    if ($major -lt 21) {
        throw "JDK 21+ required; JAVA_HOME points at $major ($versionLine)."
    }
}

Write-Host "JDK      : $versionLine" -ForegroundColor DarkGray
Write-Host "profile  : $Env" -ForegroundColor DarkGray

# --- build ---------------------------------------------------------------
# Rebuild when asked, when the jar is missing, or when any source file is
# newer than it. A timestamp comparison rather than always building, because
# `mvn package` dominates the wall-clock time of a 1-second dev run.
$needsBuild = $Build -or -not (Test-Path $jarPath)
if (-not $needsBuild) {
    $jarStamp = (Get-Item $jarPath).LastWriteTimeUtc
    $newest = Get-ChildItem -Path (Join-Path $projectRoot 'src'), (Join-Path $projectRoot 'pom.xml') `
        -Recurse -File | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if ($newest -and $newest.LastWriteTimeUtc -gt $jarStamp) {
        Write-Host "sources newer than jar ($($newest.Name)); rebuilding" -ForegroundColor DarkGray
        $needsBuild = $true
    }
}

if ($needsBuild) {
    $mvnArgs = @('-q', 'package')
    if ($SkipTests) { $mvnArgs += '-DskipTests' }
    Write-Host "building : mvn $($mvnArgs -join ' ')" -ForegroundColor DarkGray
    # Foreground, output streaming to this console: a build whose log is
    # hidden is a build you cannot diagnose.
    & mvn @mvnArgs
    if ($LASTEXITCODE -ne 0) { throw "build failed with exit code $LASTEXITCODE" }
}

# --- run -----------------------------------------------------------------
# Working directory is the project root, not scripts/: config/ and
# ./output are both resolved relative to it.
Push-Location $projectRoot
try {
    $appArgs = @("--pipeline.env=$Env") + ($PipelineArgs | Where-Object { $_ -ne '--' })

    # -XX:+UseParallelGC, not the G1 default: this workload is a short batch of
    # short-lived objects (events, batches, aggregates) with no latency target,
    # which is exactly the shape a throughput collector is for. Comment it out
    # to compare -- the difference is measurable at a million events.
    $jvmArgs = @(
        '-XX:+UseParallelGC',
        '-Xms256m',
        '-Xmx1g',
        # Fail fast rather than swapping if the batch size and queue capacity
        # combine into more memory than expected.
        '-XX:+ExitOnOutOfMemoryError'
    )

    Write-Host "running  : java $($jvmArgs -join ' ') -jar target\$jarName $($appArgs -join ' ')" -ForegroundColor DarkGray
    Write-Host ''

    & $javaExe @jvmArgs -jar $jarPath @appArgs
    $exit = $LASTEXITCODE

    switch ($exit) {
        0   { Write-Host "`nOK (exit 0)" -ForegroundColor Green }
        2   { Write-Host "`nconfiguration error (exit 2) -- nothing was started" -ForegroundColor Yellow }
        130 { Write-Host "`ninterrupted after a clean drain (exit 130)" -ForegroundColor Yellow }
        default { Write-Host "`nFAILED (exit $exit)" -ForegroundColor Red }
    }

    # Propagate, do not translate. A wrapper that always exits 0 makes every
    # scheduled run look successful.
    exit $exit
} finally {
    Pop-Location
}
