FROM ubuntu:24.04 AS build

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        build-essential \
        ca-certificates \
        clang \
        cmake \
        flex \
        bison \
        git \
        llvm-dev \
        mlir-18-tools \
        ninja-build \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
COPY . .

RUN cmake -S . -B build -G Ninja -DPLANG_ENABLE_TESTS=ON \
    && cmake --build build \
    && ctest --test-dir build --output-on-failure

FROM ubuntu:24.04 AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates libstdc++6 \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/build/plang /usr/local/bin/plang
ENTRYPOINT ["plang"]
