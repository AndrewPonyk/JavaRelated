# Technical Notes — `perflib`

Actionable engineering guidance for building, testing, benchmarking, and
shipping a hand-tuned x86-64/AVX2 routine library.

---

## 3.1 CI/CD Pipeline Design

Stages, in order. Each stage gates the next; the pipeline is **fail-fast**.

```text
lint ─► build ─► test ─► bench-regression ─► package ─► release
```

| Stage | What runs | Gate |
|---|---|---|
| **lint** | `clang-format --dry-run --Werror`, `cppcheck`, NASM `-Worphan-labels`, shellcheck | Style/static-analysis clean |
| **build** | Matrix: `{gcc, clang} × {Debug, Release}`; assemble NASM; link `.a`/`.so` | Zero warnings (`-Wall -Wextra -Werror`) |
| **test** | `make test` — equivalence (asm==C, ULP-bounded) + edge cases | All green |
| **bench-regression** | `make bench` on a **pinned** runner; compare medians to stored baseline | Speedup must not drop > threshold (e.g. 5%) |
| **package** | `make package` → versioned tarball + headers + `pkg-config` + `sha256` | Artifact produced & checksummed |
| **release** | On `v*` tag: upload artifacts to GitHub Release | Tag matches `Makefile` version |

**Environments:** `dev` (every PR: lint+build+test, bench is informational only),
`staging`/nightly (full bench on a dedicated, performance-stable runner — turbo
and governor pinned), `prod`/release (tag-triggered packaging).

> ⚠️ **Microbenchmarks on shared CI runners are noisy.** Treat PR-time bench
> numbers as *directional*; enforce the hard regression gate only on a
> dedicated, isolated, frequency-pinned runner (self-hosted or bare-metal).

See `.github/workflows/ci.yml` and `release.yml` for the concrete jobs.

---

## 3.2 Testing Strategy

**Correctness is the contract for an optimization library** — a faster wrong
answer is worthless. Three layers:

1. **Unit / equivalence tests (`tests/`)** — the core gate.
   - For every routine, assert the AVX2 kernel equals the C reference.
   - Integer/byte routines (`strlen`, `memchr`): **exact** equality.
   - Float routines (`saxpy`, `sdot`, `sgemm`): **ULP-bounded** equality — FMA
     and different summation orders legitimately change the last bits, so test
     `|a-b| <= tol * max(1,|ref|)` (relative) plus an absolute floor.
   - **Edge cases are mandatory:** length 0/1/7/8/9 (around the 8-lane vector
     width), unaligned pointers (offset 1..31), and the **page-boundary** case
     (string ending exactly at the last byte of a guard page) to prove no
     over-read.
   - Framework: a tiny header-only `CHECK/CHECK_EQ` (`tests/test_framework.h`) —
     no external dependency, builds everywhere. (Drop-in upgrade path: Unity,
     greatest, or Criterion if richer reporting is wanted.)
   - **Coverage target:** 100% of public routines have an equivalence test and
     edge-case suite; ≥90% line coverage on `src/c` (the reference logic).
     Assembly coverage is asserted behaviorally (equivalence), not by line.

2. **Property / fuzz tests (Phase 3).** Random length × alignment × content for
   string ops; random shapes/values (fixed seed) for matrix ops. Run longer in
   nightly. Optionally drive with libFuzzer/AFL on the C reference for OOB bugs.

3. **Benchmark "tests".** The bench harness self-checks: it verifies each
   measured kernel produces the reference result *before* timing it, so you can
   never publish a number for a wrong kernel.

**Run locally:** `make test` (debug, asserts on) and
`make test SANITIZE=1` (ASan/UBSan) before pushing.

---

## 3.3 Deployment Strategy

This is a **library**, so "deployment" = *distribution*, not a running service.

- **Artifacts:** `libperflib.a` (static), `libperflib.so.MAJOR.MINOR.PATCH`
  (shared, with SONAME `libperflib.so.MAJOR`), public headers, `perflib.pc`
  (pkg-config), `LICENSE`, and a `sha256sum` manifest.
- **Packaging:** `make package` → `dist/perflib-<version>-<arch>.tar.gz`.
  Downstream packagers can wrap this in `.deb`/`.rpm`/Homebrew/vcpkg/Conan.
- **Versioning:** SemVer. ABI-breaking change → MAJOR + SONAME bump. Additive
  symbols → MINOR. Internal kernel improvements (same ABI/results) → PATCH.
- **Containerization (for build/bench reproducibility, not for shipping):** the
  `Dockerfile` pins gcc/clang + NASM so anyone reproduces identical binaries and
  comparable benchmark numbers. The container is a *tool*, not the deliverable.
- **Consumption:** `cc app.c $(pkg-config --cflags --libs perflib)` or vendor the
  static lib. Call `perflib_init()` once at startup.

---

## 3.4 Environment Management

Configuration is build/bench-time (there is no runtime service config). Driven by
environment variables / Make overrides; see `.env.example`.

| Variable | Purpose | Dev | Staging/nightly | Release |
|---|---|---|---|---|
| `CC` | C compiler | `gcc` | `gcc` & `clang` | pinned |
| `AS` | Assembler | `nasm` | `nasm` | pinned |
| `BUILD` | `debug`/`release` | `debug` | `release` | `release` |
| `ARCH_FLAGS` | e.g. `-march=native` / `-mavx2 -mfma` | `native` | fixed target | fixed target |
| `SANITIZE` | ASan/UBSan on | `1` | `0` | `0` |
| `BENCH_SIZES` | matrix/string sizes | small | full sweep | — |
| `BENCH_REPEATS` | timed samples | few | many | — |
| `BENCH_OUT` | results path | `bench.csv` | `nightly.json` | — |
| `CPU_PIN` | core to pin bench to | unset | fixed | — |

> Keep `-march=native` for *dev convenience only*. For published numbers and
> shipped binaries use an **explicit** target (`-mavx2 -mfma -mtune=…`) so
> results are attributable and binaries are portable to the documented baseline.

Copy `.env.example` → `.env` and `source` it (or pass as `make VAR=…`). Never
commit a real `.env`.

---

## 3.5 Version Control Workflow

**Trunk-based development with short-lived branches** + protected `master`.

Rationale: a small library with a hard CI gate benefits from a single
always-releasable trunk over the ceremony of Gitflow. Releases are just **tags**
on trunk (`vX.Y.Z`), which trigger `release.yml`.

- Branch per change: `feat/sgemm-blocking`, `fix/strlen-pagecross`,
  `perf/sdot-unroll`, `docs/…`.
- PR required; CI (lint→build→test) must pass; ≥1 review.
- **Performance-affecting PRs must attach before/after bench numbers** from the
  pinned environment (the bench job posts them automatically).
- Squash-merge to keep trunk linear; tag releases from trunk.
- Conventional-commit prefixes (`feat:`, `fix:`, `perf:`, `docs:`, `test:`,
  `build:`) so changelogs and SemVer bumps can be derived.

---

## 3.6 Common Pitfalls (x86-64 / AVX2 / NASM)

The expensive, specific traps for *this* stack:

1. **Forgetting `vzeroupper`.** Returning to SSE code with dirty upper YMM state
   causes severe AVX↔SSE transition penalties. Every AVX routine must
   `vzeroupper` before `ret`. (Tested indirectly via bench stability.)

2. **Calling-convention mismatch (the #1 portability bug).** System V AMD64
   (Linux/macOS): integer args `rdi, rsi, rdx, rcx, r8, r9`, floats `xmm0–7`,
   callee-saved `rbx, rbp, r12–r15`. **Windows x64** is different: args
   `rcx, rdx, r8, r9` (+ shadow space, and `xmm6–15` are callee-saved!). Kernels
   here target System V by default — building/calling them on Windows requires
   the MSYS2/MinGW System V-style path or a per-ABI variant. Don't assume.

3. **Unaligned loads / page-crossing reads.** `vmovaps` on a non-32B-aligned
   pointer **faults**. Use `vmovups` unless alignment is guaranteed. For string
   scans, align *down* to 32B and mask the head — a 32B-aligned load never
   crosses a 4 KiB page (4096 % 32 == 0), which is what makes `strlen` page-safe.

4. **Float reduction order ≠ scalar.** Vectorized/FMA summation gives different
   (often *more* accurate) low bits than left-to-right scalar. Tests must be
   **ULP/relative-tolerance** based, never bitwise, for float reductions.

5. **Benchmark methodology errors** (these produce confident, wrong conclusions):
   - **Cold caches / no warmup** → first run dominated by misses.
   - **Turbo & frequency scaling** → unpinned governor makes runs incomparable;
     pin the core, set `performance` governor, disable turbo for published runs.
   - **Dead-code elimination** → the compiler deletes your benchmark if the
     result is unused. Consume the output (accumulate, write to `volatile`,
     or a `DoNotOptimize` sink).
   - **Wrong unit / forgetting `2*M*N*K` FLOPs for gemm.**
   - **Comparing against `-O0`** instead of `-O3 -march=native` — that's a straw
     man; the honest baseline is the *auto-vectorized* compiler output.

6. **NASM vs GAS syntax & sections.** This project is **NASM** (Intel syntax,
   `section .text`, `default rel`). Don't paste AT&T/`.intel_syntax` snippets.
   Mark the stack non-executable (`section .note.GNU-stack noalloc noexec` on
   ELF) or the linker warns and may mark the lib executable-stack.

7. **PIC / relocations in shared libs.** Use `default rel` and RIP-relative
   addressing for any data references so the `.so` is position-independent.

8. **`tzcnt`/`lzcnt`/BMI assumptions.** They need BMI1; safe on any AVX2 CPU
   (Haswell+), but if you ever lower the baseline, `tzcnt` silently decodes as
   `rep bsf` (different semantics for input 0). Gate on detected features.

9. **Denormals / FTZ.** If inputs can be denormal, kernels may run far slower
   than the benchmark of "normal" data suggests. Decide and document whether
   FTZ/DAZ (`MXCSR`) is set.
