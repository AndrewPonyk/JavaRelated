# Custom CPU Emulator

An educational, cycle-aware **x86-64 subset emulator** written in C with NASM
helpers, an SDL2 visualizer, and a GDB-friendly debug workflow. It makes the
*fetch → decode → execute → retire* cycle of a CPU **observable**, and estimates
how a program would perform across several real microarchitectures.

> **Stack:** C11 · NASM (x86-64) · SDL2 · GNU Make · GDB
> **Status:** working MVP — the core (registers, memory, ALU/flags, interrupts,
> timing) is verified by an automated test suite; the x86 instruction subset is
> still expanding (see `docs/PROJECT-PLAN.md`).

---

## Requirements status

How the implementation maps to the stated requirements (✅ met & verified ·
🟡 implemented at MVP depth — see `docs/PROJECT-PLAN.md` for the phased roadmap):

| Requirement | Status | Reality |
|---|---|---|
| **Register file** | ✅ Met | 16 GPR + RIP + RFLAGS; correct 8/16/32/64-bit aliasing (incl. 32-bit zero-extend); unit-tested. |
| **Memory** | ✅ Met | Flat, byte-addressable, bounds-checked, overflow-safe, little-endian; tested. |
| **ALU implementation** | ✅ Met | Exact CF/PF/AF/ZF/SF/OF; truth-table tested. The strongest part. |
| **Timing on real CPU models** | ✅ Met | 5 microarchitectures; demonstrated live (Skylake 11 cyc vs 8086 58 cyc on the same program). |
| **Instruction decoder** | 🟡 Functional, partial | Real REX/ModR/M/SIB/disp/imm decoder, ~20 encodings; straight-line **and backward-branch loop** programs verified end-to-end. Many common groups (`0xFF`/`0xF7` → INC/DEC/MUL/DIV, two-byte `0x0F`, string ops) still raise `#UD`. |
| **Interrupt handling** | ✅ Met | IDT, `INT n`/`IRET`, `#DE`/`#UD`/`#GP`, stack frame, unhandled-vector fault path — integration-tested: full `INT → handler → IRET` round-trip, unhandled-fault halt, and `#UD` delivery to a handler. |
| **Visualization** | 🟡 Structural only | SDL2 window, panel layout, step/run/reset controls — but text rendering (`SDL_ttf`) isn't wired, so panels are frames, not glyphs. Off by default. |

## Features

- **Architectural state** — 16 general-purpose registers with correct x86-64
  sub-register aliasing (8/16/32/64-bit), RIP, and RFLAGS.
- **Bounds-checked flat memory** — guest code can never crash the host.
- **Exact condition flags** — CF/PF/AF/ZF/SF/OF computed per the Intel SDM.
- **Subset decoder** — REX, ModR/M, SIB, displacement, immediate.
- **Interrupts & exceptions** — 256-entry IDT, `INT n`/`IRET`, `#DE`/`#UD`/`#GP`.
- **Timing model** — per-microarchitecture cycle estimates (8086 → Skylake).
- **SDL2 visualizer** — live registers, flags, disassembly, stack, memory, cycles.
- **Snapshots** — versioned save/restore of full machine state.
- **Headless mode** — for CI, scripting, and differential testing.

## Prerequisites

| Need | Linux / macOS | Windows |
|------|---------------|---------|
| C11 compiler | gcc or clang | MSVC (VS2022) or clang |
| Build tool | GNU Make **or** CMake ≥ 3.16 + Ninja | CMake + Ninja (bundled with VS2022) |
| NASM *(optional)* | `nasm` — only for `make fixtures` and the ASM ALU path | `nasm` |
| SDL2 *(optional)* | `libsdl2-dev` — only for the GUI build | SDL2 dev libraries |

The headless core, the loader, and the full test suite need **only a C11 compiler
and a build tool**. NASM and SDL2 are optional and off by default, so the project
builds and tests clean on a bare toolchain.

### Build with CMake (cross-platform; required on Windows/MSVC)

```bash
cmake -S . -B build -G Ninja          # add -DCPUEMU_ENABLE_GUI=ON for the SDL2 GUI
cmake --build build
ctest --test-dir build --output-on-failure
./build/cpuemu --selftest --headless
```

## Quick start (GNU Make)

```bash
# Build (SDL2 GUI auto-detected; headless if SDL2 is absent)
make                      # or: make core   to force headless

# Run the built-in self-test
make selftest             # → [selftest] PASS (eax=12, halted)

# Assemble a guest program and run it
make fixtures
./build/cpuemu --program tests/programs/add.bin --model skylake --headless

# Tests
make test

# Reproducible build/test in Docker
docker build -t cpuemu . && docker run --rm cpuemu
```

## Usage

```text
cpuemu [options]
  -p, --program FILE    flat binary to load and execute
  -m, --model ID        8086 | i486 | pentium | core2 | skylake
      --mem-size N        guest RAM in bytes (hex ok)
      --load-addr ADDR    load/entry address (default 0x1000)
      --max-steps N       stop after N instructions (0 = unlimited)
      --headless          run without the SDL2 window
      --restore FILE      restore a .ces snapshot before running
      --snapshot FILE     save a .ces snapshot on exit
      --selftest          run a tiny built-in program and verify
      --log-level L       trace|debug|info|warn|error|fatal
  -h, --help
```

Configuration can also come from environment variables (see `.env.example`):
`CPUEMU_LOG_LEVEL`, `CPUEMU_CPU_MODEL`, `CPUEMU_MEM_SIZE`, `CPUEMU_HEADLESS`,
`CPUEMU_MAX_STEPS`, `CPUEMU_LOAD_ADDR`. Precedence: **CLI > env > default**.

### Visualizer controls

| Key | Action |
|-----|--------|
| `SPACE` | single-step one instruction |
| `C` | run to `HLT` |
| `R` | reset the machine |
| `ESC` / `Q` | quit |

## Project layout

```text
src/common/   types, config, logging          (libc only)
src/core/     registers, memory, alu, decoder, interrupts, timing, cpu
src/loader/   flat-binary load + snapshot save/restore
src/gui/      SDL2 visualizer (optional build layer)
src/asm/      hand-written NASM ALU helper (optional)
tests/        unit + integration + NASM guest fixtures
docs/         PROJECT-PLAN · ARCHITECTURE · TECH-NOTES
```

The core engine is a **pure, deterministic** layer with no I/O or UI
dependencies; the GUI and loader depend on it, never the reverse. See
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Documentation

- [`docs/PROJECT-PLAN.md`](docs/PROJECT-PLAN.md) — structure, phased TODO, milestones.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — pattern, diagrams, data flow, security.
- [`docs/TECH-NOTES.md`](docs/TECH-NOTES.md) — CI/CD, testing, deployment, pitfalls.

## A note on the timing model

The cycle counts are deliberately simplified **teaching estimates** that capture
the *relative* evolution of microarchitectures (e.g. an 8086's microcoded
multiply/divide costing dozens of cycles vs. near-single-cycle ALU ops on
Skylake). They are not cycle-exact and do not yet model the pipeline or caches —
that is on the roadmap. See `src/core/timing.c` for the tables and their sources.

## Roadmap (highlights)

- Complete the decoder/execute coverage of the v1 opcode subset.
- Stack engine + CLI debugger (breakpoints, watchpoints, register edits).
- SDL_ttf text rendering in the visualizer; pipeline/hazard view.
- Performance: threaded dispatch, decoded-instruction cache, block JIT.
- Stretch: protected mode + paging, an SSE subset, and a WebAssembly build.

## Troubleshooting

| Symptom | Cause / fix |
|---------|-------------|
| `make` produces a headless binary (no window) | SDL2 wasn't found. Install `libsdl2-dev` and rebuild, or use CMake with `-DCPUEMU_ENABLE_GUI=ON`. |
| `make fixtures` fails / no `.bin` produced | `nasm` isn't installed. Install NASM, or just use `--selftest` (its program is embedded — no NASM needed). |
| Guest halts with an "unhandled vector" warning | The program ran `INT n` with no handler for that vector, or hit an unsupported opcode (`#UD`). Install a handler via the IDT, or stay within the supported subset. |
| `load failed: memory access out of bounds` | The image doesn't fit at `--load-addr` within `--mem-size`. Increase `--mem-size` or lower `--load-addr`. |
| Windows: `vcruntime140d.dll not found` at runtime | A Debug build needs the VS dev environment. Run from a Developer prompt (or after `vcvars64.bat`), or build Release: `cmake --build build --config Release`. |
| `clang-format`/`clang-tidy` step fails | Style/static-analysis finding — run `make format` to auto-fix, then `make format-check`. |
| Log lines show literal `\x1b[..m` | The terminal isn't interpreting ANSI color escapes; cosmetic only. |

## License

Released under the [MIT License](LICENSE).
