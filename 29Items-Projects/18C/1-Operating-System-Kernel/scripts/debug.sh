#!/usr/bin/env bash
# debug.sh — Launch QEMU halted with a GDB stub, then attach GDB.
#
# QEMU boots stopped (-S) and listens on :1234 (-s). We load the unstripped ELF
# for symbols. See TECH-NOTES §3.6 (triple faults are silent — use -d here).
set -euo pipefail

KERNEL="${1:-build/kernel.elf}"

qemu-system-x86_64 \
    -kernel "$KERNEL" \
    -serial stdio \
    -display none \
    -no-reboot \
    -d int,cpu_reset \
    -s -S &
QEMU_PID=$!
trap 'kill "$QEMU_PID" 2>/dev/null || true' EXIT

gdb \
    -ex "target remote :1234" \
    -ex "symbol-file $KERNEL" \
    -ex "break kernel_main" \
    -ex "continue"
