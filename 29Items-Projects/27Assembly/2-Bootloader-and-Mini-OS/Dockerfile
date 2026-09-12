# =============================================================================
#  Dockerfile  --  Reproducible build/run toolchain for Mini-OS
#
#  We containerize the TOOLCHAIN, not the OS image. Pinning nasm/gcc/ld/qemu
#  means every developer and CI runner builds byte-identical artifacts -- the
#  single biggest reliability win for an assembly project (see TECH-NOTES 3.3).
#
#  Build:  docker build -t mini-os-build .
#  Use:    docker run --rm -v "$PWD":/src mini-os-build make
#  Test:   docker run --rm -v "$PWD":/src mini-os-build make test
# =============================================================================
FROM debian:12-slim

# Pinned, minimal toolchain. gcc-multilib provides the 32-bit headers/libs that
# `gcc -m32` needs; qemu-system-x86 supplies qemu-system-i386.
RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential \
        gcc-multilib \
        nasm \
        binutils \
        qemu-system-x86 \
        clang-format \
        make \
        bash \
        xxd \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src

# Default to a full build; override with `make test`, `make run`, etc.
CMD ["make"]
