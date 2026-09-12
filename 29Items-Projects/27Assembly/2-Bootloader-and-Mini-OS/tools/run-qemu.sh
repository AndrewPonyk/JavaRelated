#!/usr/bin/env bash
# =============================================================================
#  run-qemu.sh  --  Launch the OS image under QEMU with sane defaults
#
#  Sources .env (if present) for overridable knobs, then boots the raw image.
#  Pass --debug to wait for a gdb attach on :1234, or --serial for a headless
#  CI-style run that prints COM1 to stdout.
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[ -f "$ROOT/.env" ] && set -a && . "$ROOT/.env" && set +a

QEMU="${QEMU:-qemu-system-i386}"
QEMU_MEM="${QEMU_MEM:-64M}"
IMAGE="${1:-$ROOT/build/os-image.bin}"

case "${2:-}" in
    --debug)
        # Freeze at reset and wait for gdb: `target remote :1234`.
        exec "$QEMU" -drive format=raw,file="$IMAGE" -m "$QEMU_MEM" \
            -no-reboot -d int,cpu_reset -s -S
        ;;
    --serial)
        # Headless: route COM1 to stdout so CI can assert on boot output.
        exec "$QEMU" -drive format=raw,file="$IMAGE" -m "$QEMU_MEM" \
            -no-reboot -nographic -serial stdio
        ;;
    *)
        exec "$QEMU" -drive format=raw,file="$IMAGE" -m "$QEMU_MEM" -no-reboot
        ;;
esac
