$ErrorActionPreference = "Stop"

cargo fmt --all -- --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace

Push-Location frontend
try {
    npm ci
    npm test
    npm run build
}
finally {
    Pop-Location
}
