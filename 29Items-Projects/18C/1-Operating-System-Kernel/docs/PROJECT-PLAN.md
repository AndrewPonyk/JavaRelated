# Project Plan — Operating System Kernel

> **Project:** Educational x86-64 Microkernel
> **Tech Stack:** C (C17), GCC, GNU Make, GDB, QEMU, NASM (x86-64 Assembly)
> **Goal:** Demonstrate kernel concepts — memory management, process scheduling
> (incl. a simple ML-based priority heuristic), system calls, and a basic
> in-memory filesystem.

---

## 1.1 Project File Structure

The project follows a **microkernel layout**: a small privileged core (memory,
scheduling, IPC, syscalls) with services layered on top. Architecture-specific
code is isolated under `kernel/arch/` so the core stays portable.

```text
1-Operating-System-Kernel/
├── docs/                      # Architecture & technical documentation
│   ├── PROJECT-PLAN.md
│   ├── ARCHITECTURE.md
│   └── TECH-NOTES.md
│
├── boot/                      # Boot path — runs before C
│   ├── boot.asm               # Multiboot2 header + 64-bit entry stub
│   └── linker.ld              # Kernel link script (load addr, sections)
│
├── kernel/                    # The privileged microkernel core
│   ├── kmain.c                # C entry point (kernel_main)
│   ├── include/               # Public kernel headers (the "API")
│   │   ├── types.h            # Fixed-width typedefs, bool, NULL
│   │   ├── kernel.h           # Panic, assert, global config
│   │   ├── memory.h           # PMM / VMM / heap interfaces
│   │   ├── sched.h            # Scheduler & process interfaces
│   │   ├── ml_sched.h         # ML priority model interface
│   │   ├── syscall.h          # Syscall numbers + dispatch
│   │   └── vfs.h              # Virtual filesystem interface
│   │
│   ├── mm/                    # Memory management subsystem
│   │   ├── pmm.c              # Physical frame allocator (bitmap)
│   │   ├── vmm.c              # Virtual memory / 4-level paging
│   │   └── kheap.c            # Kernel heap (bump + free-list)
│   │
│   ├── sched/                 # Scheduling subsystem
│   │   ├── process.c          # PCB lifecycle, process table
│   │   ├── scheduler.c        # Round-robin + priority queues
│   │   └── ml_priority.c      # Linear-model priority predictor
│   │
│   ├── syscall/
│   │   └── syscall.c          # Syscall dispatch table & handlers
│   │
│   ├── fs/                    # Basic filesystem
│   │   ├── vfs.c              # VFS abstraction layer
│   │   └── ramfs.c            # In-memory filesystem backend
│   │
│   ├── drivers/               # Minimal device drivers
│   │   ├── vga.c              # VGA text-mode console
│   │   ├── serial.c           # 16550 UART (logging/CI output)
│   │   └── keyboard.c         # PS/2 keyboard
│   │
│   ├── arch/x86_64/           # Architecture-specific code (isolated)
│   │   ├── gdt.c              # Global Descriptor Table
│   │   ├── idt.c              # Interrupt Descriptor Table
│   │   ├── isr.asm            # ISR/IRQ stubs
│   │   └── context_switch.asm # Task context switch
│   │
│   └── lib/                   # Freestanding C library (no libc)
│       ├── string.c          # memcpy/memset/strlen/...
│       └── printf.c           # kprintf -> VGA + serial
│
├── user/                      # Userspace ("frontend" of the OS)
│   ├── shell/
│   │   └── shell.c            # Interactive shell using syscalls
│   └── lib/
│       └── usyscall.h         # Userspace syscall wrappers
│
├── tests/                     # Host-compiled unit tests
│   ├── test_framework.h       # Tiny assertion framework
│   ├── test_pmm.c             # Physical allocator tests
│   ├── test_scheduler.c       # Scheduler tests
│   └── test_ml_priority.c     # ML priority model tests
│
├── scripts/                   # Dev/run helpers
│   ├── run-qemu.sh            # Boot the kernel in QEMU
│   └── debug.sh               # QEMU + GDB remote debugging
│
├── tools/                     # Build/dev tool placeholders
│   └── README.md
│
├── config/                    # Tool configuration
│   └── grub.cfg               # GRUB menu entry for ISO boot
│
├── .github/workflows/
│   └── ci.yml                 # CI: lint → test → build → smoke-boot
│
├── Makefile                   # Top-level build (kernel + tests + iso)
├── .clang-format              # Code style
├── .gitignore
├── .env.example               # Build-time configuration knobs
└── claude-opus-4-8.txt        # Model marker file
```

### Layering rationale

| Layer            | Directory                | Depends on            |
|------------------|--------------------------|-----------------------|
| Boot             | `boot/`                  | nothing (firmware)    |
| Arch glue        | `kernel/arch/x86_64/`    | `lib/`, `include/`    |
| Memory           | `kernel/mm/`             | arch, lib             |
| Scheduling       | `kernel/sched/`          | mm, arch              |
| Syscalls / IPC   | `kernel/syscall/`        | sched, mm             |
| Services (FS)    | `kernel/fs/`             | mm, syscall           |
| Drivers          | `kernel/drivers/`        | arch, lib             |
| Userspace        | `user/`                  | syscall ABI only      |

Dependencies always point **downward**. Userspace only knows the syscall ABI,
never kernel internals — the defining property of a microkernel boundary.

---

## 1.2 Implementation TODO List

### ✅ Phase 1 — Foundation (high priority) — DONE
- [x] Multiboot2 boot stub + 64-bit long mode entry (`boot/boot.asm`)
- [x] Linker script with correct load address & sections (`boot/linker.ld`)
- [x] Freestanding `string.c` + `kprintf` over VGA and serial
- [x] GDT + IDT + ISR/IRQ stubs; remap PIC
- [x] Physical memory manager (bitmap frame allocator)
- [x] 4-level paging (VMM): map/unmap, higher-half kernel
- [x] Kernel heap allocator (`kmalloc`/`kfree`)
- [x] `panic()`, `KASSERT()`, serial-backed logging
- [x] Host unit-test harness + CI green on `make test`

### ✅ Phase 2 — Core features (medium priority) — DONE
- [x] Process control block (PCB) + process table
- [x] Context switch in assembly; kernel threads
- [x] Scheduler with priority run-queues + PIT timer (drives ML accounting)
- [x] Priority queues feeding the scheduler
- [x] System call dispatch table + argument-validation boundary
- [x] Core syscalls: `write`, `read`, `open`, `close`, `exit`, `getpid`, `yield`
- [x] VFS layer + `ramfs` backend (`open`/`read`/`write`/`close`)
- [x] PS/2 keyboard driver feeding an input ring buffer
- [x] Shell over the syscall/VFS surface (in-kernel `kshell` + ring-3 reference)

### 🎁 Phase 3 — Polish & optimization (lower priority)
- [x] ML-based priority scheduling (`ml_priority.c`): predict a process
      priority from features (CPU burst history, I/O wait, age, niceness)
- [x] Online weight updates from observed behavior (perceptron-style)
- [x] In-kernel self-test + subsystem demo over serial (`kernel/selftest.c`)
- [x] GRUB ISO target + QEMU smoke-boot assertion in CI
- [x] Dockerfile + docker-compose for a one-command build/boot/test
- [ ] True ring-3 userspace (ELF loader, user page tables, TSS, `syscall` MSRs)
- [ ] Tick-preemptive scheduling (on top of the existing context switch)
- [ ] Copy-on-write fork; demand paging; slab allocator

---

## Milestones & exit criteria

| Milestone | Definition of done                                           |
|-----------|--------------------------------------------------------------|
| M1 Boots  | Kernel prints a banner to VGA + serial under QEMU            |
| M2 Memory | `kmalloc`/`kfree` stress test passes; paging faults handled  |
| M3 Multi  | Two kernel threads round-robin via timer preemption          |
| M4 Sys    | Userspace shell runs and services syscalls                   |
| M5 FS     | `ramfs` create/read/write/list works from the shell          |
| M6 ML     | Scheduler priorities driven by the learned model; CI green   |
