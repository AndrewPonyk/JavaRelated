FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        build-essential \
        ca-certificates \
        clang \
        clang-tidy \
        clang-format \
        cmake \
        gcovr \
        git \
        libsqlite3-dev \
        ninja-build \
        sqlite3 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
COPY . /workspace

RUN cmake --preset debug && cmake --build --preset debug

CMD ["ctest", "--test-dir", "build/debug", "--output-on-failure"]
