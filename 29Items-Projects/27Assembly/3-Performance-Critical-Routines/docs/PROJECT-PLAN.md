# Performance-Critical Routines — Project Plan

> A high-performance C library of hand-tuned **x86-64 / AVX2** routines (matrix
> multiply, dot/axpy, string operations) with a rigorous benchmarking harness
> that compares hand-written assembly against the compiler's auto-vectorized C.

---

## 1. Overview

| | |
|---|---|
| **Name** | `perflib` (Performance-Critical Routines) |
| **Languages** | C11 (reference + glue), NASM x86-64 (AVX2/FMA kernels) |
| **Target ISA** | x86-64 baseline, AVX2 + FMA3 fast path, runtime-dispatched |
| **Artifacts** | Static lib `libperflib.a`, shared lib `libperflib.so`, public headers |
| **Build** | GNU Make (primary), portable to MinGW/MSYS2 on Windows |
| **CI/CD** | GitHub Actions (lint → test → bench-regression → package) |
| **Distribution** | Tarball + headers; pkg-config file; GitHub Releases |

### Design goals

1. **Correctness first.** Every assembly kernel has a portable C reference that
   the test suite proves it byte-for-byte (or ULP-bounded) equivalent to.
2. **Honest benchmarking.** Compare hand-written AVX2 against `-O3 -march=native`
   auto-vectorized C, on identical data, with warmup, steady-state sampling,
   and outlier rejection. Report GFLOP/s, GB/s, and speedup with variance.
3. **Safe by default.** Runtime CPU feature detection (`CPUID`) selects the best
   implementation; the library never executes an unsupported instruction.
4. **Zero-friction consumption.** A single `#include <perflib/perflib.h>`, a
   stable C ABI, and a `pkg-config` file.

---

## 2. Project File Structure

```text
3-Performance-Critical-Routines/
├── docs/                          # Architecture & engineering docs
│   ├── PROJECT-PLAN.md            #   ← this file
│   ├── ARCHITECTURE.md            #   patterns, data flow, diagrams
│   └── TECH-NOTES.md              #   CI, testing, pitfalls, env
│
├── include/perflib/              # PUBLIC API (the only thing consumers see)
│   ├── perflib.h                 #   umbrella header + init/version
│   ├── matrix.h                  #   BLAS-ish: sgemm, saxpy, sdot
│   ├── string_ops.h              #   strlen, memchr, ...
│   └── cpu_features.h            #   runtime ISA detection
│
├── src/
│   ├── c/                        # Portable C reference implementations
│   │   ├── matrix_c.c            #   naive + cache-blocked sgemm, saxpy, sdot
│   │   ├── string_ops_c.c        #   scalar strlen/memchr
│   │   └── cpu_features.c        #   CPUID wrapper
│   ├── asm/                      # NASM AVX2/FMA kernels
│   │   ├── matrix_avx2.asm       #   saxpy/sdot (complete), sgemm (kernel+TODO)
│   │   └── string_ops_avx2.asm   #   strlen (complete), memchr (TODO)
│   └── dispatch/
│       └── dispatch.c            #   function-pointer dispatch (C ↔ asm)
│
├── bench/                        # Benchmark harness (C vs AVX2)
│   ├── bench_util.h              #   timers, statistics, CSV emit
│   ├── bench_main.c              #   CLI: select suite, sizes, repeats
│   ├── bench_matrix.c            #   sgemm/saxpy/sdot benchmarks
│   └── bench_string.c            #   strlen/memchr benchmarks
│
├── tests/                        # Correctness (asm == C reference)
│   ├── test_framework.h          #   tiny assertion framework
│   ├── test_main.c               #   test runner
│   ├── test_matrix.c             #   numeric equivalence (ULP bounds)
│   └── test_string.c             #   edge cases: empty, unaligned, page-boundary
│
├── examples/
│   └── example_usage.c           # Minimal consumer program
│
├── schema/                       # Benchmark-result data contracts
│   ├── benchmark_results.schema.json   # JSON Schema for one bench run
│   └── results_history.sql       #   SQLite DDL for regression tracking
│
├── tools/
│   └── plot_results.py           # Render CSV/JSON → speedup charts
│
├── scripts/
│   └── run_benchmarks.sh         # Reproducible bench driver
│
├── .github/workflows/
│   ├── ci.yml                    # lint → build → test → bench-regression
│   └── release.yml               # tag → package → GitHub Release
│
├── Makefile                      # Single source of build truth
├── Dockerfile                    # Reproducible Linux build/bench env
├── .env.example                  # Build/bench configuration template
├── .clang-format                 # C style
├── .editorconfig                 # Whitespace/encoding
├── .gitignore
├── LICENSE
├── README.md
└── Opus-4.8.txt                  # (model marker file)
```

### Why this layout

- **`include/` is the contract.** Anything outside it is an implementation
  detail and may change. Consumers depend only on the stable ABI.
- **`src/c` ↔ `src/asm` mirror each other.** Each public symbol has a `_c` and
  an `_avx2` implementation; `dispatch/` binds the public symbol to the best one.
  This is what makes "C vs AVX2" benchmarking and equivalence testing trivial.
- **`bench/`, `tests/`, and `schema/` are first-class.** For a performance
  library the benchmark harness and the correctness proof *are* the product.

---

## 3. Implementation TODO List

### Phase 1 — Foundation (high priority)

- [x] Repo scaffold, Makefile, `.gitignore`, license, editor/format configs
- [x] Public headers (`perflib.h`, `matrix.h`, `string_ops.h`, `cpu_features.h`)
- [x] CPUID-based runtime feature detection (`cpu_features.c`)
- [x] Function-pointer dispatch layer (`dispatch.c`) + `perflib_init()`
- [x] C reference implementations for every routine (correctness ground truth)
- [x] CI: build matrix (gcc/clang, x86-64), assemble NASM, run unit tests
- [x] Tiny test framework + equivalence tests (C ref vs asm, ULP-bounded)

### Phase 2 — Core features (medium priority)

- [x] AVX2 `strlen` (complete, page-safe) — first real kernel
- [x] AVX2 `saxpy` / `sdot` with FMA + scalar remainder (complete)
- [ ] AVX2 `sgemm` register-blocked micro-kernel (skeleton present → finish)
  - [ ] 6×16 (or 8×8) register tile with FMA accumulation
  - [ ] Masked epilogue for N not a multiple of 8
  - [ ] L2/L1 cache blocking (loop tiling over M/N/K)
  - [ ] Software prefetch of next A/B panels
- [x] AVX2 `memchr` (32-byte chunks + scalar remainder, no over-read)
- [x] Benchmark harness: warmup, steady-state sampling, GFLOP/s & GB/s, CSV/JSON
- [ ] Regression gate in CI (smoke test present; hard gate on pinned runner TODO)

### Phase 3 — Polish & optimization (lower priority)

- [ ] Optional AVX-512 path (gated behind detection + downclock awareness)
- [x] `pkg-config` file + install target, SONAME versioning (uninstall TODO)
- [ ] `plot_results.py` → speedup/roofline charts committed as CI artifacts
- [ ] SQLite history DB + trend dashboard for nightly benchmarks
- [ ] Fuzz tests for string routines (random lengths/alignments/content)
- [ ] Windows x64 ABI variants of kernels (separate `_win64` entry or shim)
- [ ] Documentation site (API reference generated from headers)

---

## 4. Milestones / Definition of Done

| Milestone | Exit criteria |
|---|---|
| **M1 — Builds & links** | `make` produces `libperflib.{a,so}`; example links & runs |
| **M2 — Correct** | `make test` green; every asm kernel == C ref within ULP bound |
| **M3 — Measured** | `make bench` emits schema-valid JSON/CSV; charts render |
| **M4 — Gated** | CI fails on correctness regression *or* >X% perf regression |
| **M5 — Shippable** | `make package` yields versioned tarball + headers + pkg-config |

---

## 5. Risks & Mitigations (summary — see TECH-NOTES.md §3.6)

| Risk | Mitigation |
|---|---|
| Asm bug silently wrong | Equivalence tests vs C reference are mandatory CI gate |
| Misleading benchmarks (cold cache, turbo, governor) | Pinned core, warmup, steady-state median, documented governor |
| Illegal-instruction crash on old CPU | Runtime `CPUID` dispatch; baseline C path always present |
| AVX↔SSE transition penalty | `vzeroupper` before returning from AVX code |
| Non-portable ABI assumptions | Default System V; isolate Windows x64 differences in TECH-NOTES |
