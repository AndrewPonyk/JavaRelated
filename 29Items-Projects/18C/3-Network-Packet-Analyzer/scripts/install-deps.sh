#!/usr/bin/env bash
# install-deps.sh — bootstrap build + dev dependencies for npa.
# Detects the platform package manager (apt / dnf / brew) and installs:
#   build toolchain, libpcap, ncursesw, and the lint/format tools.
set -euo pipefail

have() { command -v "$1" >/dev/null 2>&1; }

echo "==> Detecting package manager..."
if have apt-get; then
    echo "==> apt detected (Debian/Ubuntu)"
    sudo apt-get update
    sudo apt-get install -y \
        build-essential pkg-config \
        libpcap-dev libncursesw5-dev \
        clang clang-tidy clang-format cppcheck \
        valgrind gcovr
elif have dnf; then
    echo "==> dnf detected (Fedora/RHEL)"
    sudo dnf install -y \
        gcc make pkgconf-pkg-config \
        libpcap-devel ncurses-devel \
        clang clang-tools-extra cppcheck \
        valgrind gcovr
elif have brew; then
    echo "==> Homebrew detected (macOS)"
    brew update
    # libpcap & ncurses ship with macOS, but Homebrew versions are newer.
    brew install libpcap ncurses llvm cppcheck
    echo "note: use 'brew --prefix ncurses' / 'libpcap' for include & lib paths"
else
    echo "error: no supported package manager (apt/dnf/brew) found" >&2
    echo "       install manually: a C11 compiler, libpcap-dev, libncursesw-dev," >&2
    echo "       clang-format, clang-tidy, cppcheck" >&2
    exit 1
fi

echo "==> Done. Build with:  make"
echo "    Grant capture rights without root:"
echo "       sudo setcap cap_net_raw,cap_net_admin+eip ./build/bin/npa"
