# Memory Allocator — Project Plan

> Educational custom `malloc`/`free` implementation in C with multiple placement
> strategies (first-fit, best-fit, buddy system), runtime debugging features, and
> allocation-pattern heuristics. Toolchain: **C11**, **Make**, **Valgrind**, **GDB**,
> **AddressSanitizer**, **GitHub Actions**.

---

## 1. Project File Structure (Code + CI + Tools)

This is a **systems / library** project, so the classic "frontend / backend / database"
split maps onto:

| Generic layer        | This project's equivalent                                            |
|----------------------|----------------------------------------------------------------------|
| Frontend / UI        | `examples/demo.c` — the CLI driver a user runs                       |
| Backend / API        | `include/memalloc.h` + `src/allocator.c` — the public allocator API |
| Business logic        | `src/strategies/*`, `src/heuristics/*` — placement & heuristics      |
| Shared modules        | `src/core/*` — heap, block metadata, free list                       |
| "Database" / schema  | In-memory **block layout** (`src/core/block.h`) — the persisted state|
| Migrations            | N/A (no durable store) — ABI/layout versioning instead (see §1.3)   |
| Configuration         | `.env.example`, `Makefile` variables, `.clang-format`, `.clang-tidy` |

```text
4-Memory-Allocator/
├── docs/
│   ├── PROJECT-PLAN.md          # This file
│   ├── ARCHITECTURE.md          # Patterns, diagrams, data flow
│   └── TECH-NOTES.md            # CI/CD, testing, workflow, pitfalls
│
├── include/
│   └── memalloc.h               # PUBLIC API (the only header consumers include)
│
├── src/
│   ├── allocator.c              # Top-level façade: dispatch to a strategy
│   ├── core/                    # Strategy-agnostic infrastructure
│   │   ├── block.h / block.c    #   Block header layout + integrity checks
│   │   ├── heap.h  / heap.c     #   OS memory backend (sbrk / arena fallback)
│   │   └── free_list.h/.c       #   Shared alloc/free/split/coalesce engine
│   ├── strategies/              # Pluggable placement policies
│   │   ├── first_fit.h / .c     #   First block that fits  (reference impl)
│   │   ├── best_fit.h  / .c     #   Smallest block that fits
│   │   └── buddy.h     / .c     #   Power-of-two buddy system (scaffold)
│   ├── debug/                   # Diagnostics
│   │   ├── debug.h / debug.c    #   Leak & double-free tracking
│   │   └── stats.h / stats.c    #   Live bytes, peak, fragmentation metric
│   └── heuristics/
│       └── pattern.h / pattern.c#   Workload analysis → strategy recommendation
│
├── tests/                       # One standalone executable per test file
│   ├── test_framework.h         #   Tiny assertion/runner header (no deps)
│   ├── test_first_fit.c
│   ├── test_best_fit.c
│   ├── test_buddy.c
│   └── test_fragmentation.c
│
├── benchmarks/
│   └── bench_strategies.c       # Throughput comparison across strategies
│
├── examples/
│   └── demo.c                   # User-facing CLI: `demo [first|best|buddy]`
│
├── scripts/
│   ├── run_valgrind.sh          # Leak check wrapper
│   └── run_gdb.sh               # Debugger launcher with handy breakpoints
│
├── .github/workflows/
│   └── ci.yml                   # Lint → build → test → ASan → Valgrind
│
├── Makefile                     # Build modes: debug / release / asan / test ...
├── .clang-format                # Code style (enforced in CI)
├── .clang-tidy                  # Static analysis rules
├── .gitignore
├── .env.example                 # Tunables (strategy, arena size, debug)
├── README.md
└── claude-opus-4-8.txt          # Model marker file
```

### 1.3 "Schema" / layout versioning note

There is no database. The closest analog to a schema is the **block header layout**
(`src/core/block.h`). Because allocated regions are not persisted across runs, no
migrations are required. However, the header carries a `magic` field and (in debug
builds) canaries; if the layout changes, bump a `BLOCK_LAYOUT_VERSION` constant so
mixed-version object files fail loudly rather than silently corrupting memory.

---

## 2. Implementation TODO List

### Phase 1 — Foundation (high priority)
- [x] Define public API (`include/memalloc.h`)
- [x] Block header layout + alignment macros (`core/block.h`)
- [x] OS memory backend with `sbrk` + portable static-arena fallback (`core/heap.c`)
- [x] Shared free-list engine: alloc, free, **split**, **coalesce** (`core/free_list.c`)
- [x] First-fit strategy (reference implementation)
- [x] Top-level dispatch façade (`allocator.c`)
- [x] Makefile with `debug`/`release`/`test` targets
- [ ] Wire up `BLOCK_LAYOUT_VERSION` guard

### Phase 2 — Core features (medium priority)
- [x] Best-fit strategy
- [ ] **Buddy system**: free-list-per-order, split-on-alloc, merge-on-free (scaffolded)
- [x] Statistics: live bytes, peak, external-fragmentation ratio
- [x] Debug tracking: leak report + double-free / bad-pointer detection
- [ ] Overflow canaries validated on `free` in debug builds
- [x] `mem_calloc` (overflow-safe) and `mem_realloc`
- [ ] `realloc` in-place tail split on shrink; in-place grow when next block free
- [x] Unit tests per strategy + fragmentation test
- [x] AddressSanitizer + Valgrind targets and CI

### Phase 3 — Polish & optimization (lower priority)
- [x] Allocation-pattern heuristic (recommend strategy from workload)
- [ ] Segregated free lists / size classes for O(1) common-case allocation
- [ ] Thread safety (per-arena locks or thread-local arenas)
- [ ] Benchmark harness with reproducible workloads + regression thresholds
- [ ] `gdb` pretty-printer (Python) for `block_header_t`
- [ ] Doxygen API docs published from CI

---

## 3. Definition of Done

A change is "done" when it: compiles clean under `-Wall -Wextra`, passes `make test`,
passes `make asan` and `make valgrind` with **zero** errors/leaks, is formatted
(`make format`), and updates the relevant doc section above.
