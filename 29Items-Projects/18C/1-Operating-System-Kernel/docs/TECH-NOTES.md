# Technical Notes — Operating System Kernel

Actionable guidance for building, testing, and shipping this kernel. Tailored to
the **C17 / GCC / Make / GDB / QEMU / NASM** stack.

---

## 3.1 CI/CD Pipeline Design

A kernel has no "deploy to prod", so the pipeline targets **reproducible builds
and boot-level smoke tests**. Stages:

```text
lint ──► test ──► build ──► smoke-boot ──► artifact
```

| Stage        | Tooling                          | Gate                                    |
|--------------|----------------------------------|-----------------------------------------|
| **lint**     | `clang-format --dry-run -Werror` | style; fails on diff                    |
| **test**     | host `gcc` + unit harness        | pure modules pass (PMM, ML, ramfs)      |
| **build**    | cross `gcc` + `ld` + `nasm`      | `kernel.elf` links cleanly, `-Werror`   |
| **smoke-boot** | `qemu-system-x86_64 -nographic`| serial log shows banner, no `PANIC`     |
| **artifact** | `grub-mkrescue`                  | `os.iso` uploaded as build artifact     |

Implementation lives in `.github/workflows/ci.yml`. Key details:

- **Headless QEMU** with `-display none -serial stdio -no-reboot` and a timeout;
  the job parses serial output for a success marker and fails on `PANIC`.
- **Two toolchains:** the host compiler for `make test`, the freestanding
  cross-compile flags (`-ffreestanding -mno-red-zone -mcmodel=kernel`) for the
  kernel itself. Keep them separate in the Makefile.
- **Cache** apt packages (`qemu-system-x86`, `nasm`, `grub-pc-bin`, `xorriso`).

---

## 3.2 Testing Strategy

Kernels are notoriously hard to test in place — so **maximize the surface that
runs on the host**.

### Unit testing (host-compiled)
- **Framework:** a tiny header-only harness (`tests/test_framework.h`) — no
  external deps, compiles freestanding logic with the host libc.
- **Targets:** anything pure/deterministic — PMM bitmap math, run-queue
  ordering, ML priority scoring/updates, ramfs path resolution.
- **Coverage target:** **≥ 80%** of `kernel/mm`, `kernel/sched`, `kernel/fs`
  logic that is hardware-independent. Measure with `gcc --coverage` + `gcov`.

### Integration testing (in QEMU)
- Boot the real kernel and run **in-kernel self-tests** gated behind a
  `CONFIG_SELFTEST` build flag; results stream over serial.
- Assert observable behavior: "two threads alternate", "page fault on bad
  access is caught", "ramfs round-trips a file".

### End-to-end testing
- Drive the **userspace shell** via QEMU serial: feed a scripted command list
  on stdin, diff serial stdout against a golden transcript.
- Tools: `expect`/`pexpect` or a small Python harness around QEMU; runs in CI's
  smoke-boot stage.

> Rule of thumb: if it can be a host unit test, it **must** be — reserve QEMU for
> things that genuinely need hardware semantics.

---

## 3.3 Deployment Strategy

"Deployment" = producing a **bootable image** runnable on QEMU (and, in
principle, real hardware).

- **Primary artifact:** `kernel.elf` (Multiboot2) booted directly via
  `qemu-system-x86_64 -kernel kernel.elf` — fastest dev loop.
- **Distributable artifact:** `os.iso` built with GRUB (`grub-mkrescue`) using
  `config/grub.cfg` — boots on QEMU, VirtualBox, or USB on bare metal.
- **Containerization:** the *build* is containerized (a Docker image pinning
  gcc/nasm/grub/qemu versions) so CI and every dev get a byte-identical
  toolchain. The kernel itself, of course, is not containerized.

```text
make            # -> build/kernel.elf
make iso        # -> build/os.iso  (GRUB)
make run        # -> qemu -kernel build/kernel.elf -serial stdio
make debug      # -> qemu -s -S ... + gdb
```

---

## 3.4 Environment Management

Configuration is **build-time**, not runtime — surfaced through Make variables
sourced from `.env` and propagated as `-D` macros.

| Environment | `BUILD` | Flags                                  | Use                       |
|-------------|---------|----------------------------------------|---------------------------|
| development | `debug` | `-O0 -g -DDEBUG -DCONFIG_SELFTEST`     | GDB, asserts, self-tests  |
| staging     | `relwd` | `-O2 -g -DNDEBUG`                       | perf with symbols (CI)    |
| production  | `rel`   | `-O2 -DNDEBUG`                          | shipped ISO               |

See `.env.example` for every knob (log level, QEMU memory, SMP cores, ML model
enable, RNG seed). Copy it to `.env` locally; **never commit `.env`**.

---

## 3.5 Version Control Workflow

**Recommended: trunk-based development with short-lived feature branches.**

- `master` is always buildable and boots in QEMU (CI-enforced).
- Feature branches: `feat/paging`, `fix/idt-double-fault`, `docs/architecture`.
- Small PRs gated on the full pipeline (lint → test → build → smoke-boot).
- Tag milestones (`v0.1-boots`, `v0.2-paging`, …) matching the plan's `M1..M6`.

**Why trunk-based here:** a solo/small educational project benefits from a
single integration point and fast feedback; Gitflow's release/hotfix ceremony
adds overhead with no payoff when there is no long-lived release train.

Conventional-commit prefixes (`feat:`, `fix:`, `docs:`, `test:`, `build:`) keep
history greppable and enable auto-changelogs later.

---

## 3.6 Common Pitfalls

Hard-won gotchas specific to **bare-metal x86-64 C**:

1. **The red zone.** The SysV ABI's 128-byte red zone is unsafe in kernel/ISR
   code (interrupts clobber it). Always build with `-mno-red-zone`.
2. **No standard library.** You are `-ffreestanding`/`-nostdlib`. `memcpy`,
   `memset`, `strlen` must be provided — and GCC may *emit calls to them* even
   if you didn't, so they must exist.
3. **Stack alignment.** The ABI requires 16-byte stack alignment at `call`
   sites; SSE instructions `#GP` otherwise. Mind this in `boot.asm` and context
   switches.
4. **Identity vs. higher-half mapping.** Mixing up physical and virtual
   addresses before paging is enabled is the #1 source of triple faults. Be
   explicit about which space every pointer lives in.
5. **`volatile` for MMIO & shared flags.** The compiler will cache reads of VGA
   memory or `volatile`-less spin flags; mark them `volatile`.
6. **PIC/APIC remap before enabling interrupts.** Default IRQ vectors collide
   with CPU exceptions (0–31). Remap the PIC or you'll mis-handle faults.
7. **Triple faults are silent.** A fault while handling a fault while handling a
   fault → CPU reset. QEMU with `-d int,cpu_reset -no-reboot` is essential to
   see what happened.
8. **GDB needs the unstripped ELF.** Debug the `kernel.elf` with symbols even
   when QEMU boots the same image; `target remote :1234` + `symbol-file`.
9. **`-O2` exposes UB.** Optimized builds reorder/eliminate code with undefined
   behavior (uninitialized vars, strict-aliasing violations). Test both `-O0`
   and `-O2`; consider `-fno-strict-aliasing` for device structs.
10. **Floating point in the kernel.** Using SSE/x87 for the ML model in ring 0
    requires enabling the FPU (`CR0`/`CR4` bits) and saving FP state across
    context switches — or keep the model in **fixed-point integer math** to
    sidestep it entirely (recommended for `ml_priority.c`).
