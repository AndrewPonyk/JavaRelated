$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath ".env")) {
    throw "Missing .env. Copy .env.example to .env and choose a local PostgreSQL password."
}

Get-Content -LiteralPath ".env" | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
        $name, $value = $line.Split("=", 2)
        [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim(), "Process")
    }
}

if (-not $env:TEST_DATABASE_URL) {
    throw "TEST_DATABASE_URL must be set in .env."
}

docker compose up -d postgres

cargo fmt --all --check
cargo clippy --workspace --all-targets --all-features --locked -- -D warnings
cargo test --workspace --all-features --locked
cargo llvm-cov --workspace --all-features --locked --summary-only --fail-under-lines 80
cargo build --workspace --release --locked
docker compose build

Write-Host "All local checks passed."
