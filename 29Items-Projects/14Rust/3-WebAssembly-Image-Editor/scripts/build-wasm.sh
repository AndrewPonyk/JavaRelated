#!/usr/bin/env sh
set -eu

if ! command -v wasm-pack >/dev/null 2>&1; then
  echo "wasm-pack is required: https://rustwasm.github.io/wasm-pack/installer/" >&2
  exit 1
fi

wasm-pack build crates/image-processor \
  --target web \
  --release \
  --out-dir ../../apps/web/public/wasm \
  --out-name image_processor

