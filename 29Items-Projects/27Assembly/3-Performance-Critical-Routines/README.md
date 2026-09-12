# perflib — Performance-Critical Routines

Hand-tuned **x86-64 / AVX2 + FMA** routines (matrix multiply, dot/axpy, string
scans) with a rigorous harness that benchmarks them against the compiler's own
auto-vectorized C. The goal is an honest answer to: *"can hand-written assembly
still beat `-O3 -march=native`, and by how much?"*

> Every assembly kernel ships with a portable C reference, and the test suite
> proves they agree (byte-exact for integer/string ops, ULP-bounded for float).
> A faster wrong answer is a bug, not a feature.

| | |
|---|---|
| **Languages** | C11 + NASM (x86-64, AVX2/FMA3) |
| **Build** | GNU Make |
| **Dispatch** | runtime `CPUID` — AVX2 fast path, C fallback, never an illegal instruction |
| **Artifacts** | `libperflib.a`, `libperflib.so`, headers, `pkg-config` |

## Routines

| Routine | Signature | Status |
|---|---|---|
| `saxpy` | `y += a*x` | ✅ AVX2 (FMA + scalar remainder) |
| `sdot`  | `Σ xᵢyᵢ`  | ✅ AVX2 (FMA + horizontal reduce) |
| `sgemm` | `C += A·B` | 🟡 AVX2 vectorized path (N%8); register-blocking & masked tail are TODO |
| `strlen`| `strlen`   | ✅ AVX2, page-safe |
| `memchr`| `memchr`   | ✅ AVX2 (32-byte chunks + scalar remainder, no over-read) |

See [`docs/PROJECT-PLAN.md`](docs/PROJECT-PLAN.md) for the full roadmap.

## Quick start

```sh
# Linux/macOS with gcc/clang + nasm installed
make              # build lib + bench + example (debug)
make test         # build & run the equivalence/edge-case suite
make run-bench BUILD=release          # quick benchmark to stdout
scripts/run_benchmarks.sh --out results.csv   # full run + charts

# or fully reproducible, in a pinned container:
docker build -t perflib-dev . && docker run --rm perflib-dev make test
```

### Use it in your program

```c
#include <perflib/perflib.h>

int main(void) {
    perflib_init();                 // detect CPU, bind fastest impls (once)
    float x[8] = {1,2,3,4,5,6,7,8}, y[8] = {0};
    perflib_saxpy(8, 2.0f, x, y);   // y = 2*x, via AVX2 if available
}
```

```sh
cc app.c -Iinclude build/release/libperflib.a -lm -o app
# or, once installed: cc app.c $(pkg-config --cflags --libs perflib)
```

## How the comparison stays honest

- **Baseline = auto-vectorized C**, not `-O0`. The C reference is written in a
  vectorizer-friendly form and built with `-O3 -march=native`.
- **Warmup + steady-state median** over many samples; outputs are consumed via
  an optimizer barrier so the work can't be deleted.
- **Correct units:** GB/s for memory-bound ops, GFLOP/s (`2·M·N·K`) for gemm.
- Results are emitted as schema-valid CSV/JSON
  ([`schema/benchmark_results.schema.json`](schema/benchmark_results.schema.json))
  and can be tracked over time for regression gating
  ([`schema/results_history.sql`](schema/results_history.sql)).

## Layout

```
include/perflib/   public API (the stable ABI)
src/c/             portable C references (truth + baseline)
src/asm/           NASM AVX2/FMA kernels
src/dispatch/      runtime CPUID dispatch
bench/  tests/     harness & correctness suite
schema/ tools/     result contracts & plotting
docs/              PROJECT-PLAN, ARCHITECTURE, TECH-NOTES
```

## Documentation

- [Architecture](docs/ARCHITECTURE.md) — pattern, data flow, scalability, security
- [Technical notes](docs/TECH-NOTES.md) — CI, testing, deployment, **AVX2 pitfalls**
- [Project plan](docs/PROJECT-PLAN.md) — structure & phased TODO

## Requirements & caveats

- **CPU:** AVX2 + FMA3 (Intel Haswell / AMD Excavator or newer) for the fast
  path. Older CPUs transparently use the C reference.
- **ABI:** kernels target the **System V AMD64** convention (Linux/macOS,
  MinGW-sysv). Windows x64 has a different convention — see TECH-NOTES §3.6.
- **macOS:** Mach-O prefixes C symbols with `_`; the NASM kernels are ELF-first
  (the documented/tested target is Linux).

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `nasm: command not found` | Install NASM (`apt install nasm`, `brew install nasm`). It is a build-time requirement alongside a C compiler. |
| `Illegal instruction (core dumped)` running tests/bench | The CPU lacks AVX2/FMA. The library itself falls back to C, but the **tests/bench call the `_avx2` symbols directly**. Run on an AVX2 host, or under an emulator that supports it. `grep avx2 /proc/cpuinfo` to check. |
| `relocation R_X86_64_... cannot be used making a shared object` | Old NASM emitting `PC32` for branches. Use NASM ≥ 2.13 (emits `PLT32`); the kernels are `default rel` / PIC-clean. |
| Wrong results / crash on **Windows (MSVC/native)** | The kernels use the **System V AMD64** ABI (args in `rdi,rsi,...`). Windows x64 passes args in `rcx,rdx,r8,r9`. Build under WSL/MinGW-sysv or Docker, not MSVC. See TECH-NOTES §3.6. |
| Link error for `_perflib_*` on **macOS** | Mach-O prefixes C symbols with `_`. The NASM kernels are ELF-first; build on Linux (or add an underscore shim). |
| Benchmarks noisy / non-reproducible | Pin a core (`CPU_PIN` in `.env` → `taskset`), set the `performance` governor, disable turbo. See `docs/TECH-NOTES.md §3.6`. |
| `make` "missing separator" | A recipe line lost its leading **tab** (Makefiles require real tabs, not spaces). |
| Tests fail only with `SANITIZE=1` | ASan/UBSan caught a real issue — read its report; do not silence it. |

## License

MIT — see [LICENSE](LICENSE).
