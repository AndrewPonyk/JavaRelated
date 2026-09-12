#!/usr/bin/env bash
# compile_shaders.sh — compile GLSL in assets/shaders to SPIR-V using glslc.
# Output .spv files land next to the sources (git-ignored). Requires the Vulkan SDK
# (glslc on PATH or VULKAN_SDK set). Mirrors cmake/modules/FindShaderc.cmake.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SHADER_DIR="${1:-$SCRIPT_DIR/../../assets/shaders}"

# Locate glslc.
GLSLC="${GLSLC:-}"
if [[ -z "$GLSLC" ]]; then
    if command -v glslc >/dev/null 2>&1; then
        GLSLC="$(command -v glslc)"
    elif [[ -n "${VULKAN_SDK:-}" && -x "$VULKAN_SDK/bin/glslc" ]]; then
        GLSLC="$VULKAN_SDK/bin/glslc"
    else
        echo "error: glslc not found. Install the Vulkan SDK or set VULKAN_SDK/GLSLC." >&2
        exit 1
    fi
fi

echo "Using glslc: $GLSLC"
echo "Compiling shaders in: $SHADER_DIR"

shopt -s nullglob
count=0
for src in "$SHADER_DIR"/*.vert "$SHADER_DIR"/*.frag "$SHADER_DIR"/*.comp; do
    out="${src}.spv"
    echo "  $(basename "$src") -> $(basename "$out")"
    "$GLSLC" -O --target-env=vulkan1.3 "$src" -o "$out"
    count=$((count + 1))
done

echo "Compiled $count shader(s)."
