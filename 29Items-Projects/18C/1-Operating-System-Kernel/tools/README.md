# tools/

Placeholder for project-specific development tooling. Suggested additions as the
kernel grows:

- `mkfs-ramfs.py` — pack a host directory into a ramfs image embedded at build.
- `gdbinit` — canned GDB macros (`p/x $cr3`, page-table walkers, PCB dumpers).
- `addr2line.sh` — map a panic address back to `file:line` from `kernel.map`.
- `qemu-trace.sh` — wrap QEMU with `-d int,cpu_reset,guest_errors` for triage.

Keep one-off scripts here so the repo root stays clean.
