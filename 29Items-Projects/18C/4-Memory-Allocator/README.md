# Memory Allocator

An **educational** custom `malloc`/`free` in C with multiple placement
strategies, runtime debugging features, and allocation-pattern heuristics.
Built to be read, instrumented (Valgrind/GDB/ASan), and extended.

> Tech stack: **C11 · Make · Valgrind · GDB · AddressSanitizer · GitHub Actions**

---

## Features

- **Pluggable strategies** — first-fit, best-fit (shared engine), and a buddy-system
  scaffold — selectable at runtime via `mem_set_strategy`.
- **Real allocator mechanics** — address-ordered block list, block **splitting** and
  neighbour **coalescing**, 16-byte aligned payloads, overflow-safe `calloc`, `realloc`.
- **Debugging** — leak snapshot + double-free / bad-pointer detection (`-DMEM_DEBUG`),
  block magic integrity checks, and an external-**fragmentation** metric.
- **Heuristics** — observes request sizes and recommends a strategy for the workload.

## Layout

```
include/memalloc.h   Public API (the only header you include)
src/allocator.c      Façade: dispatch + calloc/realloc
src/core/            heap (sbrk/arena), block layout, free-list engine
src/strategies/      first_fit, best_fit, buddy
src/debug/           stats, leak/double-free tracking
src/heuristics/      allocation-pattern recommendation
tests/ benchmarks/ examples/ scripts/
```

See [`docs/PROJECT-PLAN.md`](docs/PROJECT-PLAN.md),
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md), and
[`docs/TECH-NOTES.md`](docs/TECH-NOTES.md) for the full design.

## Build & run

The allocator core is portable C11: on Linux it uses `sbrk`, and on Windows it
falls back to a static arena automatically — so it builds and runs **natively on
both**. Use **CMake** for a cross-platform build, or the **Makefile** on Linux.

### Platform support — read this first

| Task | Windows (MSVC) | Linux / WSL2 |
|------|:--------------:|:------------:|
| Build library · demo · benchmark | ✅ | ✅ |
| Unit tests (`ctest` / `make test`) | ✅ | ✅ |
| AddressSanitizer — leak/overflow detection | ✅ `-DMEMALLOC_ASAN=ON` | ✅ |
| **Valgrind** leak check (`make valgrind`) | ❌ **not available** | ✅ |
| **GDB** debug scripts (`scripts/run_gdb.sh`) | ❌ **not available** | ✅ |

> ⚠️ **Valgrind and GDB are Linux-only — they do not exist on Windows.**
> `make valgrind` and the `scripts/run_*.sh` helpers therefore require **Linux or
> WSL2**. They are *not* a bug on Windows; the tools simply aren't ported there.
>
> **On native Windows, use AddressSanitizer as the Valgrind substitute** — it
> catches leaks, use-after-free, and buffer overflows (verified working under
> MSVC 19.42):
>
> ```bat
> cmake -S . -B build-asan -DMEMALLOC_ASAN=ON -DMEMALLOC_DEBUG=ON
> cmake --build build-asan
> ctest --test-dir build-asan --output-on-failure
> ```
>
> For the full Valgrind/GDB workflow, run the project under **WSL2** (`make valgrind`).
> Everything else — building, all unit tests, the demo, and the benchmark — runs
> natively on Windows.

### Windows (native, MSVC)

One command — it auto-loads the Visual Studio environment and the CMake/Ninja
bundled with VS, then configures, builds, and runs the tests:

```bat
scripts\build-windows.bat            :: Debug build + ctest
scripts\build-windows.bat Release    :: optimised build

build-win\bin\demo.exe best          :: run the demo
build-win\bin\bench_strategies.exe   :: run the benchmark
```

(If VS is in a non-standard location, set `VSINSTALL=<path>` first, or run the
script from a *Developer Command Prompt*.)

### Any platform (CMake)

```bash
cmake -S . -B build -DMEMALLOC_DEBUG=ON   # options: MEMALLOC_ASAN, _BUILD_BENCH
cmake --build build
ctest --test-dir build --output-on-failure
./build/bin/demo best
```

### Linux / WSL (Makefile)

```bash
make debug         # -O0 -g3 -DMEM_DEBUG : demo + tests
make test          # build & run all unit tests
make asan          # AddressSanitizer + UBSan over the tests
make valgrind      # leak-check the demo  (Linux/WSL only)
make bench         # optimised throughput comparison
make release       # -O2 -DNDEBUG
make clean         # remove build/  (run when switching modes)
```

`make valgrind` requires Linux/WSL2 — see [Platform support](#platform-support--read-this-first) above.

## API at a glance

```c
#include "memalloc.h"

mem_init(MEM_BEST_FIT);
void *p = mem_malloc(128);
p = mem_realloc(p, 256);
mem_free(p);
mem_destroy();
```

## Status

First-fit and best-fit are implemented and tested; the **buddy system** is a
documented scaffold (Phase 2). See the TODO checklist in `docs/PROJECT-PLAN.md`.

## License

Educational sample — use freely.
