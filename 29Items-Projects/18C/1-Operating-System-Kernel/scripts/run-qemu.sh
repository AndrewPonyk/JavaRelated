#!/usr/bin/env bash
# run-qemu.sh — Boot the kernel in QEMU with serial forwarded to the terminal.
#
# Usage: scripts/run-qemu.sh [path/to/kernel.elf]
set -euo pipefail

KERNEL="${1:-build/kernel.elf}"
MEM="${QEMU_MEM:-128M}"

if [[ ! -f "$KERNEL" ]]; then
    echo "kernel not found: $KERNEL  (run 'make' first)" >&2
    exit 1
fi

exec qemu-system-x86_64 \
    -kernel "$KERNEL" \
    -m "$MEM" \
    -serial stdio \
    -display none \
    -no-reboot \
    ${QEMU_EXTRA:-}
