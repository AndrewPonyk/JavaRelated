# Bootloader & Mini-OS

A small, **educational x86 operating system**: a 16-bit real-mode bootloader
that switches the CPU to 32-bit protected mode and hands off to a C kernel with
VGA + serial output, PS/2 keyboard input, identity-mapped paging, a physical
frame allocator, a kernel heap, an interactive shell, and a **preemptive**
round-robin scheduler. Built with **NASM + GCC**, run under **QEMU**.

> Teaching project — runs in an emulator, not hardened for real use.

```
BIOS ─▶ boot.asm @0x7C00 ─▶ load kernel ─▶ GDT + protected mode ─▶ kernel_main() (C)
                                                                        │
   VGA + COM1 ◀── drivers ◀── IRQs (PIC/IDT) ◀── PIT timer + PS/2 keyboard
                                                                        │
                        paging · PMM · heap · preemptive scheduler · shell
```

## Quick start

With Docker (no host toolchain needed):

```bash
docker compose up                 # build the image and boot it in QEMU (serial console)
docker compose run --rm test      # run the host unit tests
docker compose run --rm build     # just produce build/os-image.bin
```

Native (Linux with `nasm`, `gcc-multilib`, `qemu-system-x86`):

```bash
make            # -> build/os-image.bin
make run        # boot it in QEMU
make test       # run host unit tests (6 suites, no QEMU)
make debug      # boot frozen for gdb:  (gdb) target remote :1234
make CROSS=i686-elf-   # build with a cross-compiler (recommended)
```

## Using it

Once booted, two spinners (`A` / `B`) animate in the top-right corner — proof the
scheduler is preempting between tasks — while a shell runs in the foreground:

```
> help
> mem            # heap + physical-frame stats
> tasks          # list scheduler tasks (current marked *)
> uptime         # seconds since boot
> echo hello
> clear
> reboot
```

Everything the kernel prints also goes to **COM1**, so `make run` plus
`-serial stdio` (or `docker compose up`) gives you a full text transcript.

## Layout

| Path | What |
|------|------|
| `boot/`   | Stage-1 bootloader: real mode → GDT → protected mode |
| `kernel/` | C kernel: drivers, CPU/interrupts/MMU, memory, scheduler, shell |
| `tools/`  | Linker script, image builder, QEMU launcher |
| `tests/`  | Host-side unit tests (native gcc, no QEMU) |
| `docs/`   | **[PROJECT-PLAN](docs/PROJECT-PLAN.md)**, **[ARCHITECTURE](docs/ARCHITECTURE.md)**, **[TECH-NOTES](docs/TECH-NOTES.md)** |

## Documentation

- **[docs/PROJECT-PLAN.md](docs/PROJECT-PLAN.md)** — file structure, phased TODO, risks.
- **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** — patterns, memory map, Mermaid data-flow diagrams.
- **[docs/TECH-NOTES.md](docs/TECH-NOTES.md)** — CI/CD, testing, deployment, x86 pitfalls.

## Status

Phases 1–3 of the project plan are implemented and working:

- ✅ Boot → protected mode → C kernel banner (VGA **and** serial)
- ✅ IDT/ISR/IRQ, PIC remap, PIT timer, PS/2 keyboard
- ✅ Identity-mapped paging, bitmap physical memory manager, coalescing heap
- ✅ **Preemptive** round-robin scheduler with real context switching
- ✅ Interactive shell + `kprintf`, panic with register dump
- ✅ 6 host unit-test suites; CI builds the image and asserts the boot banner

The next milestone is user-mode (ring 3) tasks with per-process address spaces.
See the TODO list in [PROJECT-PLAN](docs/PROJECT-PLAN.md).

## Troubleshooting

| Symptom | Cause / fix |
|---------|-------------|
| `docker compose` hangs or "cannot connect to the Docker daemon" | Start Docker Desktop; the daemon must be running. |
| `nasm: command not found` / `gcc -m32` errors (native build) | Toolchain not installed — use Docker, or install NASM + an `i686-elf` cross-compiler + QEMU (see "build natively"). On Linux, `gcc-multilib` provides `-m32`. |
| `ERROR: kernel is N sectors but boot loads only 50` | Kernel outgrew the loader. Raise `KERNEL_SECTORS` in `boot/boot.asm` **and** `tools/create_image.sh` (keep them equal). |
| QEMU reboots forever / blank then resets | A triple fault. Diagnose with `qemu-system-i386 -drive ... -no-reboot -d int,cpu_reset` — the last `v=` line shows the faulting vector and register dump. |
| Nothing appears when booting headless | Use the serial console: add `-serial stdio` (or `-serial file:serial.log`). All kernel output mirrors to COM1. |
| `bash\r: bad interpreter` on `tools/*.sh` | Windows CRLF line endings. Convert to LF (`sed -i 's/\r$//' tools/*.sh`) — the repo ships LF. |
| Can't exit QEMU (`-nographic`) | Press `Ctrl-A` then `X`. |
| `--no-warn-rwx-segments: unknown option` (old `ld`) | Needs binutils ≥ 2.39; remove that flag from `LDFLAGS` in the `Makefile` on older toolchains. |

For a deeper list of x86/NASM/GCC gotchas see [docs/TECH-NOTES.md §3.6](docs/TECH-NOTES.md).

## License

Educational sample — use freely.
