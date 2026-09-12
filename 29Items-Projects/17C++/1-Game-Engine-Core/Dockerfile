# syntax=docker/dockerfile:1
# ---------------------------------------------------------------------------
# Self-contained build/test/run image for Game Engine Core.
#
# The engine core + all CPU-side subsystems + the full test suite build with NO
# third-party packages — GPU rendering (Vulkan/GL), the OS audio device (SDL2),
# Lua, ONNX, and SQLite are *optional* backends layered on top. So this image
# needs only a C++20 toolchain, and the build is fully reproducible.
#
#   docker build -t game-engine-core .         # builds + runs the test suite
#   docker run --rm game-engine-core           # runs the headless smoke test
# ---------------------------------------------------------------------------
FROM ubuntu:24.04 AS build

ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential clang cmake ninja-build ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src
COPY . .

# Configure + build + run the tests as part of the image build (fails the build
# if any test fails — the image is only produced from a green tree).
RUN cmake -S . -B build -G Ninja \
        -DCMAKE_BUILD_TYPE=RelWithDebInfo \
        -DCMAKE_C_COMPILER=clang -DCMAKE_CXX_COMPILER=clang++ \
        -DENGINE_BUILD_TESTS=ON -DENGINE_BUILD_EDITOR=ON \
        -DENGINE_ENABLE_VULKAN=OFF -DENGINE_ENABLE_OPENGL=OFF \
 && cmake --build build \
 && ctest --test-dir build --output-on-failure

# Default command: run the engine headless smoke test (boots every subsystem,
# renders 5 frames via the Null backend, exits 0).
CMD ["./build/bin/GameEngineCore", "--smoke-test"]
