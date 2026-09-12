# Dockerfile — reproducible build & test environment for the Custom CPU Emulator.
#
#   docker build -t cpuemu .
#   docker run --rm cpuemu                 # runs the self-test (headless)
#   docker run --rm cpuemu --help
#   # GUI (Linux host with X11):
#   docker run --rm -e DISPLAY=$DISPLAY -v /tmp/.X11-unix:/tmp/.X11-unix cpuemu \
#       --program tests/programs/add.bin
#
# Pinned base for reproducibility.
FROM ubuntu:24.04 AS build

ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential \
        clang \
        nasm \
        libsdl2-dev \
        make \
        gdb \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src
COPY . .

# Build, assemble fixtures, and run the test suite as a build-time gate.
RUN make core && make test && make fixtures && make

# ---- slim runtime image ----------------------------------------------------
FROM ubuntu:24.04 AS runtime
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
        libsdl2-2.0-0 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /src/build/cpuemu /usr/local/bin/cpuemu
COPY --from=build /src/tests/programs /app/tests/programs

ENTRYPOINT ["cpuemu"]
CMD ["--selftest", "--headless"]
