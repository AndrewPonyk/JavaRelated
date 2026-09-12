# Operating System Kernel

An educational **x86-64 microkernel** that actually boots. It demonstrates the
core OS concepts end-to-end: long-mode bring-up, physical & virtual memory
management, a context-switching scheduler driven by a small **ML-based priority
model**, a system-call boundary, a VFS + in-memory filesystem, hardware
interrupts (timer + keyboard), and an interactive shell.

On boot it runs an in-kernel **self-test suite** and a **subsystem demo** over
the serial console, then halts.

> **Stack:** C17 · GCC · GNU Make · NASM · GDB · QEMU · GRUB

---

## What it does & business logic

This is an **educational operating-system kernel**. Its "business logic" is to
demonstrate, in working code, how the core pieces of an OS fit together on
(emulated) x86-64 hardware — with one headline feature: **ML-based process
priority scheduling**. Instead of a fixed priority, each process's scheduling
band is *predicted* from its recent runtime behaviour.

There is no network or human end-user here: the **users are processes**, the
**requests are system calls**, and the **data store is an in-memory filesystem**.
The end-to-end flow the kernel exercises on every boot:

1. **Boot** — GRUB → 64-bit long mode → paging → `kernel_main`.
2. **Bring-up** — descriptors, interrupts, timer, memory allocators, filesystem.
3. **Scheduling (the core logic)** — kernel threads are multiplexed onto the CPU.
   On each enqueue the **ML model** scores the process from its features
   (CPU-burst EMA, I/O-wait EMA, age, niceness) into a priority band; the timer
   continuously updates those features, so a CPU-hog is automatically **demoted**
   and a starved/interactive task **promoted** — visible live in the boot demo as
   workers sliding from band 1 → band 2.
4. **Services** — processes use the syscall boundary to read/write files in the
   RAM filesystem and print to the console; the shell drives these.

---

## Quick start (Docker — nothing to install but Docker)

```bash
docker build -t oskernel-dev .                  # one-time toolchain image

# Boot the kernel in QEMU (serial output to your terminal):
docker run --rm -v "${PWD}:/src" -w /src oskernel-dev make run

# Run the host unit tests:
docker run --rm -v "${PWD}:/src" -w /src oskernel-dev make test
```

…or with Compose:

```bash
docker compose up kernel     # build the ISO + boot it in QEMU
docker compose up test       # host unit tests
docker compose run shell     # interactive toolchain shell
```

### Native (Linux, or WSL2 on Windows)

```bash
sudo apt-get install build-essential nasm qemu-system-x86 grub-pc-bin grub-common xorriso
make            # build build/kernel.elf
make test       # host unit tests
make run        # boot headless in QEMU (serial -> terminal), runs the demo, halts
make run-shell  # boot into the LIVE interactive shell (QEMU window)
make iso        # build bootable build/os.iso (GRUB)
make debug      # boot under QEMU + GDB on :1234
make clean
```

| Target           | What it does                                                     |
|------------------|-----------------------------------------------------------------|
| `make`           | Build `build/kernel.elf` (`BUILD=debug\|relwd\|rel`)             |
| `make test`      | Compile & run the host unit tests (13 tests)                    |
| `make run`       | Build ISO + boot headless; runs self-tests + demo, then halts   |
| `make run-graphic` | Same, in a QEMU display window                                |
| `make run-shell` | Boot into the interactive shell (rebuilds with `CONFIG_INTERACTIVE`) |
| `make iso`       | Produce a bootable `build/os.iso`                               |
| `make debug`     | Boot stopped under QEMU's GDB stub on `:1234`                   |
| `make lint` / `make format` | clang-format check / auto-fix                       |

> **Windows note:** the kernel is ELF64 + higher-half, which native MinGW can't
> build cleanly — use Docker or WSL2. Host *unit tests* work with any gcc.

### Interactive shell

By default the boot demo runs a **scripted** command sequence and then halts (so
it terminates cleanly in CI). For a **live prompt**, run `make run-shell` (or
`docker run --rm -it -v "${PWD}:/src" -w /src oskernel-dev \
make run-shell`) — after the demo, the kernel hands the keyboard to the shell:

```
ksh$ help
commands: help echo cat write ls ps meminfo uptime exit
ksh$ write greeting.txt hello kernel
wrote 12 bytes to greeting.txt
ksh$ ls
ksh$ cat greeting.txt
ksh$ ps          # list processes + their ML priority band
ksh$ meminfo     # free physical frames
```

---

## What you'll see

Booting prints the bring-up log, then:

```
==================== KERNEL SELF-TESTS ====================
pmm:        [ ok ] ... kheap: [ ok ] ... vmm: [ ok ] ...
vfs/ramfs:  [ ok ] ... ml priority: [ ok ] ... syscall ABI: [ ok ] ...
SELFTEST: 25 passed, 0 failed
SELFTEST: ALL PASSED
==========================================================
-- scheduler demo: spawning 3 worker threads --
    [worker-A] iteration 1/3 ...  [worker-B] iteration 1/3 ...   (interleaved)
-- ML priority model: band for different process profiles --
   cpu-bound -> band 7   io-bound -> band 0   starved -> band 0
-- shell demo (scripted) --
   ksh$ ls / cat / ps / meminfo / uptime ...
DEMO COMPLETE
KERNEL: HALT
```

## Architecture (matches `docs/ARCHITECTURE.md`)

| Subsystem   | Path                       | What it does                                       |
|-------------|----------------------------|----------------------------------------------------|
| Boot        | `boot/`                    | Multiboot2 → 64-bit long mode → higher-half paging |
| Memory      | `kernel/mm/`               | bitmap PMM, 4-level paging VMM, free-list heap      |
| Scheduling  | `kernel/sched/`            | priority run-queues + fixed-point ML priority model |
| Interrupts  | `kernel/arch/x86_64/`      | GDT, IDT, ISR stubs, PIC remap, PIT, context switch |
| Syscalls    | `kernel/syscall/`          | validated Ring3↔Ring0 dispatch                      |
| Filesystem  | `kernel/fs/`               | VFS abstraction + ramfs backend                     |
| Drivers     | `kernel/drivers/`          | VGA console, 16550 serial, PS/2 keyboard            |
| Shell       | `kernel/shell/`            | interactive shell over the real subsystems          |
| Self-tests  | `kernel/selftest.c`        | in-QEMU subsystem tests printed over serial         |

## ML-based scheduling

`kernel/sched/ml_priority.c` is a tiny **linear model in fixed-point (Q16.16)
integer math** — no FPU state to save across context switches. It maps a feature
vector (CPU-burst EMA, I/O-wait EMA, age, niceness) to a priority band, learns
online via a perceptron-style update, and is purely **advisory**: the scheduler
clamps the result and ages waiting tasks so a bad prediction can never starve
the system.

## System-call ABI (the kernel's "API")

Userspace talks to the kernel only through these calls
(`kernel/include/syscall.h`). Every pointer/length is validated at the boundary
(`kernel/syscall/syscall.c`); a negative return is `-errno`.

| #  | Name     | Args (rdi, rsi, rdx) | Returns                  |
|----|----------|----------------------|--------------------------|
| 0  | `read`   | fd, buf, len         | bytes read, or `-errno`  |
| 1  | `write`  | fd, buf, len         | bytes written, or `-errno` |
| 2  | `open`   | path, flags          | fd, or `-errno`          |
| 3  | `close`  | fd                   | 0, or `-errno`           |
| 4  | `exit`   | code                 | (does not return)        |
| 5  | `getpid` | —                    | current pid              |
| 6  | `yield`  | —                    | 0                        |
| 7  | `spawn`  | (ring-3 — see Scope) | `-EINVAL` for now        |

**Shell commands** (`kernel/shell/kshell.c`): `help`, `echo <text>`,
`cat <file>`, `write <file> <text>`, `ls`, `ps`, `meminfo`, `uptime`, `exit`.

## Testing

- **Host unit tests** (`make test`): PMM allocator, kernel heap (alloc/coalesce/
  overflow), the ML model, the VFS+ramfs flow (incl. error paths), and scheduler
  bookkeeping — compiled with the host gcc, **28 tests**.
- **In-kernel self-tests** (`kernel/selftest.c`): exercise PMM, heap, VMM
  (mapping a fresh page and reading it back), VFS/ramfs round-trips, the ML
  model, and the syscall ABI live in QEMU — **25 checks**.
- **CI** (`.github/workflows/ci.yml`): lint → host tests → build (ELF + ISO) →
  **smoke-boot**. The kernel signals success to QEMU via the `isa-debug-exit`
  device (host exit code **33**); CI asserts that code *and* the serial markers
  `SELFTEST: ALL PASSED` / `DEMO COMPLETE` / `KERNEL: HALT`, failing on any
  `PANIC`. That boot — "self-tests pass and the system halts cleanly" — is this
  project's **health check**.

**Dependencies** are declared in the [`Dockerfile`](Dockerfile) (the build
manifest: gcc, nasm, ld, qemu, grub, xorriso, gdb) — no language package manager
applies to a freestanding kernel.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `Cannot connect to the Docker daemon` | Start Docker Desktop / the daemon, then retry. |
| `grub-mkrescue: command not found` | Install `grub-pc-bin grub-common xorriso` (the Docker image already has them). |
| `make run` prints nothing | The kernel boots via **GRUB ISO**, not QEMU's `-kernel`. Use `make run` (builds the ISO); serial goes to your terminal. |
| QEMU window is black | Output is on the **serial console**. Use `make run` (serial→terminal) or `make run-graphic`. |
| Boot ends in `*** KERNEL PANIC ***` | A CPU exception fired. Reproduce under `make debug` (QEMU GDB stub + `-d int,cpu_reset`) and inspect the frozen state. |
| `make run` seems to "hang" after `KERNEL: HALT` | Expected — the kernel halts the CPU. Quit QEMU with `Ctrl-a x` (serial) or `Ctrl-c`. |
| Host tests crash with SIGSEGV | The scheduler host build needs `-DKERNEL_HOSTTEST` (the Makefile sets it) so privileged `cli`/`sti` compile out. |
| Native build fails on Windows | MinGW can't emit ELF64 / higher-half — use Docker or WSL2. Host *unit tests* work with any gcc. |

## Documentation

- [`docs/PROJECT-PLAN.md`](docs/PROJECT-PLAN.md) — file structure & phased TODO
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — pattern, diagrams, data flow
- [`docs/TECH-NOTES.md`](docs/TECH-NOTES.md) — CI/CD, testing, pitfalls

## Scope & honest boundaries

This is an educational kernel built to *run and be read*, not a production OS.
Two deliberate simplifications, documented rather than hidden:

1. **Scheduling is cooperative** (threads yield at well-defined points) with a
   live timer driving ML feature accounting. Full tick-preemption is a small
   extension on top of the existing context switch.
2. **The shell and syscalls run in-kernel.** The syscall *dispatch + argument
   validation boundary* is real and tested (`kernel/syscall/`), and the ring-3
   reference shell in `user/shell/shell.c` speaks the same ABI — but a true
   ring-3 privilege transition (ELF loader, per-process user page tables, TSS,
   `syscall`/`sysret` MSRs) is the natural next step, not yet wired up.

Everything else in the docs is implemented and verified by the tests above.
