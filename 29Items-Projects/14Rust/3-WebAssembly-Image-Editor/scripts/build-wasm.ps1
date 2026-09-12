$ErrorActionPreference = "Stop"

if (-not (Get-Command wasm-pack -ErrorAction SilentlyContinue)) {
    throw "wasm-pack is required. Install it from https://rustwasm.github.io/wasm-pack/installer/"
}

wasm-pack build crates/image-processor `
    --target web `
    --release `
    --out-dir ../../apps/web/public/wasm `
    --out-name image_processor

