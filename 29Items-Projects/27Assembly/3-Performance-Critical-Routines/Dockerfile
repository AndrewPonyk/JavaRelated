# Dockerfile — reproducible build/bench environment (a TOOL, not the artifact).
#
# Pins the toolchain so binaries and benchmark numbers are reproducible.
#   docker build -t perflib-dev .
#   docker run --rm -it perflib-dev make test
#   docker run --rm -v "$PWD:/src" perflib-dev make run-bench BUILD=release
#
# NOTE: microbenchmarks inside a container share the host CPU/governor — use it
# for correctness and relative trends, not for publishable absolute numbers.
FROM debian:bookworm-slim

ARG DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential \
        gcc \
        clang \
        nasm \
        make \
        cppcheck \
        clang-format \
        python3 \
        python3-matplotlib \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src
COPY . /src

# Default: build everything and run the test suite.
CMD ["make", "test"]
