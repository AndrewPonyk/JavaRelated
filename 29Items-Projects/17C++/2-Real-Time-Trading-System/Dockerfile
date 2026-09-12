# ============================================================================
#  Dockerfile — C++ trading engine (default self-contained build).
#  Multi-stage: build + run unit/integration tests with GCC, then ship a slim
#  runtime image. `docker build` failing the test stage fails the image.
# ============================================================================
FROM ubuntu:24.04 AS build

RUN apt-get update && apt-get install -y --no-install-recommends \
        g++ cmake ninja-build ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY CMakeLists.txt ./
COPY cmake ./cmake
COPY src ./src
COPY apps ./apps
COPY tests ./tests
COPY config ./config

RUN cmake -S . -B build -G Ninja -DCMAKE_BUILD_TYPE=Release -DRTS_BUILD_TESTS=ON \
    && cmake --build build --parallel \
    && ctest --test-dir build --output-on-failure

# ---- Runtime ---------------------------------------------------------------
FROM ubuntu:24.04 AS runtime

RUN apt-get update && apt-get install -y --no-install-recommends \
        libstdc++6 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /app/build/trading_engine /usr/local/bin/trading_engine
COPY --from=build /app/build/backtest /usr/local/bin/backtest
COPY config ./config

# Runs the deterministic simulation and writes the journal to /app/var/journal.
CMD ["trading_engine", "config/trading_engine.yaml"]
