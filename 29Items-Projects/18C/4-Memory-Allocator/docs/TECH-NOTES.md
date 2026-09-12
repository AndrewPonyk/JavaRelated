# Memory Allocator — Technical Notes

Actionable guidance for building, testing, and maintaining this C allocator.

---

## 3.1 CI/CD Pipeline Design

The pipeline is intentionally a **linear quality gate** (see `.github/workflows/ci.yml`).
Memory bugs are silent, so the pipeline's job is to make them loud.

```text
 lint/format ─► build (debug) ─► unit tests ─► AddressSanitizer ─► Valgrind ─► static analysis
   clang-format   make debug     make test       make asan          make valgrind   clang-tidy
   (hard fail)    (hard fail)    (hard fail)     (hard fail)         (hard fail)     (advisory)
```

| Stage            | Tool                         | Gate                                      |
|------------------|------------------------------|-------------------------------------------|
| Format           | `clang-format --dry-run`     | Hard fail on any diff                     |
| Compile          | `make debug` (`-Wall -Wextra`)| Hard fail on warning-as-error in CI       |
| Unit tests       | `make test`                  | Hard fail on any failed assertion         |
| Sanitizers       | `make asan` (ASan+UBSan)     | Hard fail on any report                   |
| Dynamic analysis | `make valgrind`              | Hard fail (`--error-exitcode=1`)          |
| Static analysis  | `make lint` (clang-tidy)     | Advisory now → promote to gate at Phase 3 |

**Environments:** there is no "deploy". The deploy analog is **publishing a
versioned artifact** — a tagged release containing `libmemalloc.a` + `memalloc.h`.
Stages map to environments as: every PR → `debug`; merge to `master` → `release`
build + benchmark; tag `vX.Y.Z` → packaged artifact (Phase 3).

---

## 3.2 Testing Strategy

- **Unit tests** — one standalone executable per `tests/test_*.c`, using the tiny
  dependency-free `tests/test_framework.h` (assertion macros + a runner). Chosen
  over Unity/Criterion to keep the repo self-contained and the build trivial. Each
  test owns the global allocator via `mem_init`/`mem_destroy` for isolation.
  **Coverage target: ≥ 85% of `src/` lines, 100% of `free_list.c` and each strategy.**
- **Property / invariant tests** — for an allocator the highest-value tests assert
  *invariants*, not values: never return overlapping regions; returned pointers are
  16-byte aligned; `free` then `malloc` of equal size reuses space; coalescing
  restores a single block. Add randomized alloc/free sequences (a fuzz-lite loop)
  and check invariants after each step.
- **Integration tests** — compile a sample program against the *installed* header
  and static lib to verify the public ABI is usable exactly as a consumer would.
- **Dynamic analysis as test** — `make asan` and `make valgrind` are first-class
  test stages. ASan catches overflow/UAF fast in CI; Valgrind catches leaks and
  uninitialized reads with no recompilation needed. Use `gcov`/`lcov` for coverage.
- **Benchmarks (not pass/fail, but tracked)** — `benchmarks/bench_strategies.c`
  compares throughput; wire a regression threshold once a baseline exists.

---

## 3.3 Deployment Strategy

This is a library, so "deployment" = **distribution**:

- **Static library** `libmemalloc.a` is the primary artifact (deterministic, no
  runtime dependency). Optionally a shared `.so` for `LD_PRELOAD` experiments that
  override the system `malloc`.
- **Containerization** is for *reproducible builds/tests*, not runtime. A small
  `Dockerfile` based on `debian:stable-slim` with `build-essential valgrind clang`
  pins the toolchain so "works on my machine" equals "works in CI". (Listed as a
  Phase 3 nicety; the Makefile already runs on any Linux/WSL host.)
- **Install** via `make install PREFIX=/usr/local` copying `memalloc.h` →
  `include/` and `libmemalloc.a` → `lib/` (target to be added).

> **Platform note:** development happens on Windows, but Valgrind and `sbrk` are
> Linux-only. Build and test under **WSL2** or a Linux container. The code includes
> a portable static-arena fallback so it still *compiles and unit-tests* on native
> Windows/MSVC, but `make valgrind` requires Linux.

---

## 3.4 Environment Management

The library reads optional, non-sensitive tunables at `mem_init`. Configuration is
the same across dev/staging/prod — only the **build mode** differs (debug vs.
release). See `.env.example`:

```dotenv
# .env.example — optional allocator tunables (read at startup)
MEMALLOC_STRATEGY=first      # first | best | buddy
MEMALLOC_ARENA_SIZE=1048576  # bytes; buddy arena size
MEMALLOC_DEBUG=0             # 1 = enable runtime checks even in release
MEMALLOC_LOG_LEVEL=warn      # off | warn | info | debug
```

| Build      | Flags                                  | Use                                  |
|------------|----------------------------------------|--------------------------------------|
| `debug`    | `-O0 -g3 -DMEM_DEBUG`                   | Local dev, GDB, leak/canary checks   |
| `asan`     | `-O1 -g -fsanitize=address,undefined`  | CI fast bug detection                |
| `release`  | `-O2 -DNDEBUG`                         | Distribution / benchmarks            |

Keep the **build matrix in the Makefile**, not scattered in shell — one source of
truth that CI and humans both call.

---

## 3.5 Version Control Workflow

**Trunk-based development with short-lived feature branches.**

- `master` is always green (CI gates above must pass). Branch as
  `feat/buddy-merge`, `fix/coalesce-prev`, `docs/architecture`; open a PR; squash-merge.
- Rationale: a single-maintainer/small-team systems library does not need the
  ceremony of Gitflow's `develop`/`release` branches. Trunk-based keeps integration
  continuous, which matters because allocator changes interact subtly — long-lived
  branches hide coalescing/splitting regressions until a painful merge.
- **Tag releases** `vMAJOR.MINOR.PATCH`. Bump MAJOR when `block_header_t` layout or
  the public API changes (ABI break). Maintain a `CHANGELOG.md` from PR titles.
- **Commit hygiene:** imperative subject, body explains *why*. Every commit must
  build and pass `make test` (enables `git bisect`, which is gold for allocator bugs).

---

## 3.6 Common Pitfalls (C / allocator-specific)

1. **Header/payload pointer arithmetic** — always convert with `block_payload()` /
   `block_from_payload()`; never hand-compute offsets. A wrong offset corrupts the
   neighbouring block silently.
2. **Alignment** — return pointers must satisfy the strictest type's alignment (16B
   here). `BLOCK_HEADER_SIZE` is rounded up so the payload that follows stays aligned.
3. **Coalescing direction** — merge with **both** neighbours and fix `prev`/`next`
   on the *survivor* and its new neighbour. Forgetting the far pointer creates a
   dangling link that crashes on the next traversal.
4. **Split threshold** — only split when the remainder can hold a header + minimum
   payload; otherwise you create unusable zero/negative-size "blocks".
5. **`realloc` semantics** — `realloc(NULL, n)` == `malloc(n)`; `realloc(p, 0)` ==
   `free(p)` + return NULL. Easy to get wrong; covered by tests.
6. **`calloc` overflow** — `nmemb * size` can wrap; check before allocating.
7. **`sbrk` is non-reentrant and effectively deprecated** — fine for teaching, but
   mixing it with the libc `malloc` (which also uses it) in the same process is
   undefined in practice. Keep the custom allocator's regions separate; prefer
   `mmap` for a real implementation.
8. **Strict aliasing & `-O2`** — accessing the same memory as both `block_header_t`
   and raw bytes is fine via `char*`, but be wary of type-punning under optimization.
9. **Use-after-free in tests** — ASan/Valgrind exist precisely because these are
   invisible to the compiler. Run them *before* trusting a green unit-test run.
10. **Windows ≠ Linux** — `sbrk`, `valgrind`, and `-fsanitize` behave differently or
    are absent on native Windows. Standardize on WSL2/Linux for the authoritative build.
