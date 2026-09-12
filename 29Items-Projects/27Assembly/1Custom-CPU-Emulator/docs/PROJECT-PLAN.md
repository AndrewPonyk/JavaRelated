# Custom CPU Emulator — Project Plan

> An educational, cycle-aware **x86-64 subset emulator** written in C with NASM
> helpers, an SDL2 visualizer, and a GDB-friendly debug workflow.
>
> **Stack:** C11 · NASM (x86-64) · SDL2 · GNU Make · GDB
> **Deployment:** Local builds via `Makefile`; reproducible builds via Docker; CI on GitHub Actions.

---

## 1. Vision & Scope

The Custom CPU Emulator is a teaching tool that makes the *fetch → decode →
execute → retire* cycle of a modern CPU **observable**. It interprets a curated
subset of x86-64 machine code, maintains a faithful architectural state
(register file, RFLAGS, flat memory), and—critically—estimates how many
**clock cycles** a program would take on several historical/real
microarchitectures (8086 → i486 → Pentium → Core 2 → Skylake).

### In scope
- A flat-memory machine model with bounds-checked access.
- An x86-64 **subset** decoder (REX prefix, ModR/M, SIB, displacement, immediate).
- An ALU that computes results **and** the full condition-flag set (CF/PF/AF/ZF/SF/OF).
- Software & hardware interrupt handling with a 256-entry IDT and CPU exceptions.
- A per-microarchitecture **timing model** (latency/throughput tables).
- A real-time **SDL2 visualizer** (registers, flags, memory hex, disassembly, stack, cycle counter).
- Save/restore of machine **snapshots** (the emulator's "persistence layer").
- A headless mode for CI and scripting.

### Explicitly out of scope (v1)
- Full x86-64 ISA coverage, paging/protected-mode/MMU, SIMD (SSE/AVX), and a JIT.
  These are tracked as roadmap items (see Phase 3 and `ARCHITECTURE.md §2.4`).

---

## 2. Project File Structure

```text
1Custom-CPU-Emulator/
├── docs/
│   ├── PROJECT-PLAN.md         # ← this file
│   ├── ARCHITECTURE.md         # patterns, diagrams, data flow, security
│   └── TECH-NOTES.md           # CI/CD, testing, deployment, pitfalls
│
├── src/
│   ├── common/                 # cross-cutting primitives (no deps on core)
│   │   ├── types.h             # enums: registers, flags, opcodes, models, status codes
│   │   ├── config.h            # build-time constants & feature flags
│   │   ├── log.h               # leveled logging API (log_trace … log_fatal)
│   │   └── log.c
│   │
│   ├── core/                   # the CPU "engine" — pure, deterministic, no I/O libs
│   │   ├── registers.h/.c      # register file + RFLAGS  (THE STATE SCHEMA)
│   │   ├── memory.h/.c         # flat byte-addressable RAM, bounds-checked
│   │   ├── alu.h/.c            # arithmetic/logic + condition-flag computation
│   │   ├── decoder.h/.c        # x86-64 subset instruction decoder + disassembler
│   │   ├── interrupts.h/.c     # IDT, software/hardware IRQs, CPU exceptions
│   │   ├── timing.h/.c         # per-microarchitecture cycle accounting
│   │   ├── cpu.h/.c            # orchestration: fetch-decode-execute-retire loop
│   │   └── (state aggregated in `struct cpu`)
│   │
│   ├── loader/                 # I/O boundary: programs in, snapshots in/out
│   │   ├── loader.h/.c         # flat-binary loader + versioned save/restore
│   │
│   ├── gui/                    # presentation layer (SDL2) — optional at build time
│   │   ├── visualizer.h/.c     # window, panels, input handling, render loop
│   │
│   ├── asm/                    # hand-written host-side NASM helpers (optional)
│   │   └── alu_fast.asm        # System V AMD64 flag-exact add (PUSHFQ trick)
│   │
│   └── main.c                  # entrypoint: arg parsing, wiring, run loop
│
├── tests/
│   ├── test_framework.h        # tiny header-only assert/TAP harness (no deps)
│   ├── unit/
│   │   ├── test_registers.c    # sub-register aliasing (AL/AX/EAX/RAX), flags
│   │   ├── test_alu.c          # result + flag truth tables
│   │   ├── test_memory.c       # bounds, endianness, width helpers
│   │   └── test_decoder.c      # encoding → instruction round-trips
│   ├── integration/
│   │   └── test_programs.c     # load tiny programs, run, assert final state
│   └── programs/               # NASM guest fixtures (assembled by `make fixtures`)
│       ├── add.asm
│       ├── loop.asm
│       └── interrupt_demo.asm
│
├── tools/                      # dev scripts (placeholder for fixture gen, perf)
│
├── .github/workflows/ci.yml    # lint → build (gcc+clang) → test → coverage → artifact
├── Makefile                    # all | core | gui | test | fixtures | format | lint | run | debug | clean
├── Dockerfile                  # reproducible Ubuntu build/test image
├── .env.example                # runtime configuration template
├── .clang-format               # code style (LLVM-derived)
├── .clang-tidy                 # static analysis rules
├── .gitignore
└── README.md
```

### Module responsibility matrix

| Layer        | Module        | Responsibility                                   | Depends on            |
|--------------|---------------|--------------------------------------------------|-----------------------|
| Cross-cutting| `common`      | types, config, logging                           | libc only             |
| Core engine  | `registers`   | architectural register/flag state                | common                |
| Core engine  | `memory`      | flat RAM, safe read/write, endianness            | common                |
| Core engine  | `alu`         | results + CF/PF/AF/ZF/SF/OF                       | common                |
| Core engine  | `decoder`     | bytes → `instruction_t`, disassembly             | common, memory        |
| Core engine  | `interrupts`  | IDT, IRQs, exceptions                             | common, registers, memory |
| Core engine  | `timing`      | cycle cost per microarchitecture                 | common, decoder       |
| Core engine  | `cpu`         | the F-D-E-retire loop; ties the core together    | all core modules      |
| I/O boundary | `loader`      | load programs, save/restore snapshots            | common, cpu           |
| Presentation | `gui`         | SDL2 rendering & input (read-mostly view of cpu) | common, cpu (read)    |
| Entrypoint   | `main`        | CLI parsing, wiring, headless vs. GUI run        | everything            |

> **Design rule:** dependencies point *inward*. `core` never includes SDL2 or
> `loader`; the GUI and loader depend on the core, never the reverse. This keeps
> the engine pure, deterministic, and trivially unit-testable. See
> `ARCHITECTURE.md §2.1`.

---

## 3. Implementation TODO List

Legend: `[ ]` todo · `[~]` partial/stubbed in this scaffold · `[x]` done.

### Phase 1 — Foundation (HIGH priority)
- [x] Repository layout, Makefile, CI skeleton, docs.
- [x] `common/types.h` — register/flag/opcode/model/status enums.
- [x] `common/log` — leveled logging with env-driven level.
- [x] `core/registers` — register file + sub-register width aliasing + flags (unit-tested).
- [x] `core/memory` — flat RAM, bounds checks, little-endian width helpers (unit-tested).
- [x] `core/alu` — ADD/SUB/CMP/logic/shift with exact flag computation (unit-tested).
- [x] Golden flag truth-table tests (`tests/unit/test_alu.c`).
- [~] Decoder: REX + ModR/M + SIB + disp/imm — implemented & tested for the v1 subset; full ISA coverage pending.

### Phase 2 — Core Features (MEDIUM priority)
- [x] `core/cpu` — fetch-decode-execute-retire loop, HLT handling (integration-tested).
- [x] `core/interrupts` — IDT, `INT n`, `IRET`, `#DE`/`#UD`/`#GP` (integration-tested: round-trip, unhandled fault, #UD delivery).
- [x] `core/timing` — latency/throughput tables for 5 microarchitectures (tested).
- [x] `loader` — flat-binary load + versioned snapshot save/restore (round-trip tested).
- [~] Stack engine: PUSH/POP/CALL/RET implemented; stack *visualization* pending.
- [ ] CLI debugger: breakpoints, single-step, watchpoints, register edits.
- [~] Integration tests: programs (add/sub/loop), interrupt round-trip & snapshot done (`tests/integration/`); assembling the NASM fixtures still needs `nasm`.

### Phase 3 — Polish & Optimization (LOWER priority)
- [~] `gui/visualizer` — SDL2 panels (regs, flags, memory, disasm, stack, cycles).
- [ ] Performance: computed-goto / tail-call dispatch; optional NASM ALU path.
- [ ] Pipeline visualization (stage occupancy, hazards) for the timing model.
- [ ] Roadmap: protected mode + paging, SSE subset, threaded JIT, Wasm build.
- [ ] Tutorial pack: annotated example programs + guided lessons in `docs/`.

### Definition of Done (per feature)
1. Public API documented in its header.
2. Unit tests cover happy path + at least two edge cases.
3. `make format lint` is clean; CI green on gcc **and** clang.
4. No new compiler warnings under `-Wall -Wextra -Werror`.
5. Memory-clean under `valgrind`/ASan for the headless path.

---

## 4. Milestones

| Milestone | Exit criteria                                                                 |
|-----------|------------------------------------------------------------------------------|
| **M0** Scaffold | Repo builds (headless), `make test` runs the harness, CI green.        |
| **M1** Engine   | `add.asm` & `loop.asm` execute to correct final register/flag state.   |
| **M2** Timing   | Cycle estimates within documented tolerance for the fixture programs.  |
| **M3** Visual   | SDL2 view renders live state; single-step from the UI.                 |
| **M4** Debugger | Breakpoints + watchpoints + snapshot save/restore usable end-to-end.   |

---

## 5. Risks & Mitigations (summary)

| Risk                                        | Mitigation                                            |
|---------------------------------------------|-------------------------------------------------------|
| x86 decoding is intricate & easy to get wrong | Constrain to a documented subset; table-driven decode; round-trip tests. |
| Flag computation subtle (AF, OF edge cases) | Golden truth tables derived from hardware/SDM; isolate in `alu`. |
| C undefined behavior (shifts, signed overflow) | Do arithmetic in `uint64_t`, mask by width, `-fsanitize=undefined` in CI. |
| SDL2 availability/portability               | GUI is an optional build layer; headless core is the source of truth.   |
| Timing realism vs. complexity               | Document model as *estimate*; cite sources; keep tables data-only.       |

See `TECH-NOTES.md §6` for the expanded pitfalls list.
