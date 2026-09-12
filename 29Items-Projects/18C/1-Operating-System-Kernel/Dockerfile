# Dockerfile — reproducible build/run environment for the kernel.
#
# Bundles the full toolchain so the host needs nothing but Docker:
#   gcc/ld/make (build-essential) · nasm · qemu · grub + xorriso · gdb
#
# This is a DEV-TOOLCHAIN image: you mount your source at runtime (-v) rather
# than COPY-ing it in, so edits on the host are picked up without rebuilding.
#
# Build the image once:
#   docker build -t oskernel-dev .
#
# Then, from the project root:
#   docker run --rm -v "${PWD}:/src" -w /src oskernel-dev make test   # unit tests
#   docker run --rm -v "${PWD}:/src" -w /src oskernel-dev make        # build kernel.elf
#   docker run --rm -v "${PWD}:/src" -w /src oskernel-dev make run    # boot in QEMU (serial)
#   docker run --rm -v "${PWD}:/src" -w /src oskernel-dev make iso    # bootable ISO
#   docker run --rm -it -v "${PWD}:/src" -w /src oskernel-dev bash    # interactive shell
#
# `make run` boots headless: QEMU uses -serial stdio -display none and pure TCG
# emulation (no -enable-kvm), so no /dev/kvm or GPU is needed inside the container.

FROM debian:bookworm-slim

# Pin a predictable toolchain. --no-install-recommends keeps the image lean.
RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential \
        nasm \
        binutils \
        qemu-system-x86 \
        grub-pc-bin \
        grub-common \
        xorriso \
        gdb \
        make \
        clang-format \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src

# Default to building the kernel; override with `make test`, `make run`, etc.
CMD ["make"]
