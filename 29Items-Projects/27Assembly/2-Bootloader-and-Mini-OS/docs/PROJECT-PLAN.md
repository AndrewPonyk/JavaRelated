# Bootloader & Mini-OS — Project Plan

> An educational x86 operating system: a 16-bit real-mode bootloader that
> transitions to 32-bit protected mode, hands off to a C kernel with VGA text
> output, PS/2 keyboard input, a flat memory manager, and a cooperative
> round-robin scheduler. Built with NASM + GCC, run under QEMU.

---

## 1.1 Project File Structure

This is a **bare-metal** project, so the classic "frontend / backend / database"
split does not apply. The equivalent decomposition for an OS is:

| Generic web term | OS equivalent in this project |
|------------------|-------------------------------|
| Frontend (UI)    | VGA text-mode driver + keyboard driver (`kernel/drivers/`) |
| Backend (logic)  | Kernel core, interrupts, scheduler (`kernel/`, `kernel/cpu/`, `kernel/sched/`) |
| Database (state) | In-memory data structures: GDT, IDT, process table, heap (`kernel/mm/`) |
| Shared modules   | Freestanding libc subset (`kernel/lib/`) + headers (`kernel/include/`) |
| Migrations       | Boot stages / mode transitions (`boot/`) |

```
2-Bootloader-and-Mini-OS/
│
├── boot/                       # Stage-1 bootloader (16-bit real mode → 32-bit PM)
│   ├── boot.asm                # MBR entry (org 0x7C00), orchestrates boot
│   ├── disk_load.asm           # Load kernel sectors via BIOS int 0x13
│   ├── gdt.asm                 # Global Descriptor Table (flat model)
│   ├── switch_pm.asm           # Real → Protected mode transition
│   ├── print_rm.asm            # Real-mode print (BIOS int 0x10 teletype)
│   └── print_pm.asm            # Protected-mode print (direct 0xB8000 VGA)
│
├── kernel/                     # 32-bit C kernel (freestanding)
│   ├── kernel_entry.asm        # 32-bit entry stub; MUST link first (loads at 0x1000)
│   ├── kernel.c                # kernel_main(): init subsystems + become idle task
│   ├── shell.c                 # Interactive command shell (runs as a task)
│   │
│   ├── include/                # Public headers
│   │   ├── types.h             # Fixed-width types (u8/u16/u32, bool, size_t)
│   │   ├── cpu.h               # Inline hlt/cli/sti (host-safe no-ops in tests)
│   │   ├── gdt.h               # Kernel GDT install
│   │   ├── ports.h             # inb/outb/inw/outw port I/O
│   │   ├── vga.h               # Screen driver API
│   │   ├── serial.h            # COM1 UART driver API
│   │   ├── kprintf.h           # printf-family formatter
│   │   ├── keyboard.h          # Keyboard driver API
│   │   ├── idt.h               # Interrupt Descriptor Table
│   │   ├── isr.h               # Interrupt service routines / handlers
│   │   ├── pic.h               # 8259 PIC remap + EOI
│   │   ├── timer.h             # PIT (8254) channel-0 timer
│   │   ├── paging.h            # 32-bit paging (identity map)
│   │   ├── pmm.h               # Physical frame allocator
│   │   ├── memory.h            # Heap (kmalloc/kfree) + mem* helpers
│   │   ├── scheduler.h         # Preemptive round-robin scheduler
│   │   └── shell.h             # Shell task entry point
│   │
│   ├── drivers/                # Hardware-facing "frontend"
│   │   ├── vga.c               # 80×25 VGA text-mode driver (scroll, cursor, color)
│   │   ├── serial.c            # COM1 16550 UART (with loopback self-test)
│   │   └── keyboard.c          # PS/2 keyboard (scancode → ASCII, ring buffer)
│   │
│   ├── cpu/                    # CPU-level plumbing
│   │   ├── gdt.c               # Kernel-owned flat GDT (replaces boot GDT)
│   │   ├── gdt_flush.asm       # lgdt + reload segments + far jump
│   │   ├── ports.c             # Port I/O wrappers (inline asm)
│   │   ├── idt.c               # IDT table + idt_install()
│   │   ├── idt_load.asm        # lidt instruction
│   │   ├── isr.c               # Fault handlers (register dump) + IRQ dispatch
│   │   ├── isr_stubs.asm       # 32 exception + 16 IRQ stubs; stack-swap switch
│   │   ├── pic.c               # Remap PIC to 0x20–0x2F
│   │   ├── timer.c             # Configure PIT, request scheduler preemption
│   │   └── paging.c            # Identity-mapped paging; enables CR0.PG
│   │
│   ├── mm/                     # Memory management ("database" of physical RAM)
│   │   ├── memory.c            # kmalloc/kfree free-list heap (full coalescing)
│   │   └── pmm.c               # Bitmap physical frame allocator
│   │
│   ├── sched/                  # Process scheduling
│   │   └── scheduler.c         # Preemptive round-robin: idle task, ctx switch
│   │
│   └── lib/                    # Freestanding standard-library subset
│       ├── string.c            # strlen, strcmp, strncmp, strcpy, strncpy
│       ├── itoa.c              # Integer → string (dec/hex)
│       └── kprintf.c           # ksnprintf/kprintf formatter (sink-backed)
│
├── tools/
│   ├── linker.ld               # ELF link script; entry = 0x1000
│   ├── create_image.sh         # Concatenate boot.bin + kernel.bin → os-image.bin
│   └── run-qemu.sh             # Convenience launcher with sane QEMU flags
│
├── tests/                      # HOST-side unit tests (native gcc, not bare-metal)
│   ├── test_framework.h        # Tiny assert/TAP-style harness (header-only)
│   ├── test_scheduler.c        # Round-robin ordering & fairness
│   ├── test_string.c           # libc subset correctness
│   ├── test_ring_buffer.c      # Keyboard ring-buffer wrap-around
│   ├── test_pmm.c              # Physical frame allocator (bitmap)
│   ├── test_kprintf.c          # printf formatter conversions
│   └── test_heap.c             # kmalloc/kfree allocation & coalescing
│
├── .github/
│   └── workflows/
│       └── ci.yml              # Lint → host-test → build → boot-smoke-test
│
├── Makefile                    # Single source of truth for the build
├── Dockerfile                  # Reproducible toolchain (nasm, gcc, qemu, ld)
├── docker-compose.yml          # `up` = build + boot in QEMU; `run test` = tests
├── .gitignore                  # Ignore build artifacts (*.bin, *.o, build/)
├── .editorconfig               # Whitespace/indent consistency
├── .clang-format               # C formatting (kernel style)
├── .env.example                # Tunable build/run knobs
├── README.md                   # Quick start
└── docs/
    ├── PROJECT-PLAN.md         # ← this file
    ├── ARCHITECTURE.md         # Patterns, diagrams, data flow
    └── TECH-NOTES.md           # CI/CD, testing, pitfalls
```

### Why this layout

- **`boot/` vs `kernel/` hard split.** The bootloader is assembled as a *flat
  binary* (`-f bin`, `org 0x7C00`), while the kernel is assembled/compiled to
  *ELF objects* and linked. Mixing them in one directory invites build-rule
  mistakes; the physical separation mirrors the two distinct toolchain paths.
- **`kernel_entry.asm` is deliberately tiny and separate.** The linker must
  place it at the very start of the kernel image (`0x1000`) because the
  bootloader jumps there blindly. Keeping it isolated makes the link-order
  constraint obvious and hard to break.
- **Drivers, CPU, MM, and sched are peers**, not a deep hierarchy. A teaching
  OS benefits from a shallow tree where every subsystem is one `cd` away.
- **`tests/` runs on the host, not the target.** Pure logic (scheduler queue,
  ring buffer, string functions) is compiled with the *native* compiler and
  unit-tested in CI without QEMU — fast feedback. Hardware-coupled code is
  validated by a boot smoke test instead.

---

## 1.2 Implementation TODO List

### ✅ Phase 1 — Foundation (HIGH priority)

- [x] Repository skeleton, Makefile, linker script, `.gitignore`
- [x] **Stage-1 bootloader** (`boot.asm`): segment setup, save boot drive
- [x] Real-mode BIOS print (`print_rm.asm`) — "Booting..." banner
- [x] **Disk load** (`disk_load.asm`): read kernel sectors via `int 0x13`, with
      carry-flag / sector-count error checks
- [x] **GDT** (`gdt.asm`): null + flat 4 GiB code/data descriptors
- [x] **Protected-mode switch** (`switch_pm.asm`): `cli`, `lgdt`, set `CR0.PE`,
      far jump to flush pipeline, reload segment registers
- [x] **32-bit kernel entry** (`kernel_entry.asm`) → `kernel_main()`
- [x] **VGA text driver** (`vga.c`): putchar, print, color, cursor, scroll,
      clear — the "hello world" proof the handoff worked
- [x] Build produces a bootable `os-image.bin` that QEMU runs to a banner

### ✅ Phase 2 — Core Features (MEDIUM priority)

- [x] **Port I/O** primitives (`ports.c`)
- [x] **PIC remap** (`pic.c`) to vectors `0x20–0x2F` (avoid clashing CPU faults)
- [x] **IDT install** (`idt.c`, `idt_load.asm`)
- [x] **ISR/IRQ stubs** (`isr_stubs.asm`) + C dispatch (`isr.c`) for the 32 CPU
      exceptions and 16 hardware IRQs
- [x] **PIT timer** (`timer.c`): ~100 Hz tick that drives preemption
- [x] **Keyboard driver** (`keyboard.c`): IRQ1, scancode→ASCII (shift+caps),
      ring buffer
- [x] **Heap allocator** (`memory.c`): `kmalloc`/`kfree` with full coalescing,
      `memset`/`memcpy`/`memmove`
- [x] **Preemptive round-robin scheduler** (`scheduler.c`): idle task, real
      interrupt-frame context switching, `task_create`, cooperative `yield()` +
      timer-driven preemption
- [x] Two demo tasks (A/B spinners) visibly time-slicing on screen
- [x] **Host unit tests** green for scheduler, ring buffer, string, heap, pmm,
      kprintf (`make test` — 6 suites)

### ✅ Phase 3 — Polish & Optimization (LOW priority)

- [x] **Physical memory manager** (`pmm.c`): 4 KiB-frame bitmap allocator with
      region reservation (initialized from known RAM size; host-unit-tested)
- [x] Paging (`paging.c`): enable `CR0.PG`, identity-map the first 4 MiB
- [x] Serial (COM1) driver (`serial.c`) so CI asserts on output without
      screen-scraping; kprintf mirrors all output to it
- [x] Simple shell / command parser (`shell.c`): help, clear, mem, ticks,
      uptime, tasks, echo, reboot
- [x] `printf`-style formatter (`kprintf.c`); panic screen with full register dump
- [x] **Boot smoke test** asserts the banner on serial (CI `boot` job)
- [ ] Stage-2 loader — *deferred by design*: the kernel (~25 KiB) fits in the
      sectors the single-stage loader reads, so this is unnecessary today. Raise
      `KERNEL_SECTORS` until ~32 KiB; beyond that, add stage 2 (see TECH-NOTES).
- [ ] User-mode (ring 3) tasks + paging-backed per-process address spaces — the
      next real milestone (the security boundary in ARCHITECTURE.md §2.5).

---

## Definition of Done (per phase)

| Phase | Done when… | Status |
|-------|-----------|--------|
| 1 | `make run` boots in QEMU and prints the kernel banner from C | ✅ |
| 2 | Keyboard echoes into the shell; two tasks time-slice on the timer | ✅ |
| 3 | PMM + paging + serial up; CI asserts the boot banner over serial | ✅ |

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Host `gcc` can't target bare metal | High | High | Prefer `i686-elf` cross-compiler; fall back to `-m32 -ffreestanding -nostdlib` (see TECH-NOTES) |
| Kernel grows past sectors loaded by `boot.asm` | Medium | High | Parameterize sector count; Phase-3 stage-2 loader |
| Triple-fault on bad GDT/IDT (instant reboot) | Medium | Medium | Bring up incrementally; use `qemu -d int,cpu_reset -no-reboot` |
| Real/protected-mode `bits` directive mistakes | Medium | Medium | One mode per file; assert with `objdump` in CI |
