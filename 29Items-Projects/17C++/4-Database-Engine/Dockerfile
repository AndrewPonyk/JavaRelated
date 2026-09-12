# syntax=docker/dockerfile:1

# ---- Stage 1: build & test in a full toolchain image -----------------------
FROM gcc:14 AS build
WORKDIR /src

# CMake + Ninja + git (for the GoogleTest FetchContent).
RUN apt-get update \
 && apt-get install -y --no-install-recommends cmake ninja-build git ca-certificates \
 && rm -rf /var/lib/apt/lists/*

# Copy sources and build.
COPY . .
RUN cmake -S . -B build -G Ninja -DCMAKE_BUILD_TYPE=Release \
 && cmake --build build --parallel \
 && ctest --test-dir build --output-on-failure

# ---- Stage 2: slim runtime carrying only the CLI ---------------------------
FROM debian:stable-slim AS runtime
WORKDIR /app

# Non-root user for safety.
RUN useradd --create-home --uid 10001 minidb
COPY --from=build /src/build/minidb /usr/local/bin/minidb
USER minidb

# Data lives on a mounted volume so the *.db survives container restarts.
VOLUME ["/data"]
ENV MINIDB_DATA_DIR=/data
ENTRYPOINT ["minidb", "/data/minidb.db"]
