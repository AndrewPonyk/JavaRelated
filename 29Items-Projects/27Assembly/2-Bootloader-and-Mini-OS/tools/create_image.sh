#!/usr/bin/env bash
# =============================================================================
#  create_image.sh  --  Assemble the final bootable disk image
#
#  os-image.bin = boot.bin (one 512-byte sector) ++ kernel.bin (flat binary),
#  padded out to a whole number of sectors so QEMU/BIOS see clean geometry.
#
#  Usage: tools/create_image.sh <boot.bin> <kernel.bin> <out.bin>
# =============================================================================
set -euo pipefail

BOOT="${1:?need boot.bin}"
KERNEL="${2:?need kernel.bin}"
OUT="${3:?need output image path}"

SECTOR=512
# Must match boot.asm KERNEL_SECTORS: the loader unconditionally reads this many
# sectors after the boot sector, so the image must contain at least this many or
# the disk read runs off the end of the file.
KERNEL_SECTORS="${KERNEL_SECTORS:-50}"

# 1. Boot sector must be exactly one sector.
boot_size=$(wc -c < "$BOOT")
if [ "$boot_size" -ne "$SECTOR" ]; then
    echo "ERROR: $BOOT is $boot_size bytes, expected exactly $SECTOR" >&2
    exit 1
fi

# 2. Fail loudly if the kernel won't fit in the sectors the loader reads.
kernel_sectors=$(( ( $(wc -c < "$KERNEL") + SECTOR - 1 ) / SECTOR ))
if [ "$kernel_sectors" -gt "$KERNEL_SECTORS" ]; then
    echo "ERROR: kernel is $kernel_sectors sectors but boot loads only $KERNEL_SECTORS." >&2
    echo "       Raise KERNEL_SECTORS in boot/boot.asm (and here)." >&2
    exit 1
fi

# 3. Concatenate boot + kernel.
cat "$BOOT" "$KERNEL" > "$OUT"

# 4. Pad the image so it holds at least (1 boot + KERNEL_SECTORS) sectors AND
#    ends on a sector boundary -- so every sector the loader reads exists.
min_bytes=$(( (1 + KERNEL_SECTORS) * SECTOR ))
img_size=$(wc -c < "$OUT")
target=$(( img_size > min_bytes ? img_size : min_bytes ))
rem=$(( target % SECTOR ))
if [ "$rem" -ne 0 ]; then target=$(( target + SECTOR - rem )); fi
if [ "$target" -gt "$img_size" ]; then
    dd if=/dev/zero bs=1 count="$(( target - img_size ))" >> "$OUT" 2>/dev/null
fi

echo "Image: $OUT ($(wc -c < "$OUT") bytes; kernel = $kernel_sectors sectors, loader reads $KERNEL_SECTORS)"
