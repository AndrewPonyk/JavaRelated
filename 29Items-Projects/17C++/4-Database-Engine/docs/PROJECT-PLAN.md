# MiniDB — Project Plan

> An educational, embeddable database engine written in modern **C++20**.
> Core themes: **B+ Tree** indexing, **memory-mapped (mmap) I/O**, an **LRU**-managed
> buffer pool, and a **SQL-subset** parser/executor with simple **cost estimation**.

- **Status:** Working engine — Phases 1 & 2 complete; persistent, 45 tests green
- **Owner:** Database Engine Team
- **Last updated:** 2026-06-21

---

## 1. Overview

MiniDB is a single-node, single-file storage engine intended for teaching and
experimentation. It demonstrates how production databases (SQLite, Postgres,
BusTub/CMU-15445) are layered internally, while staying small enough to read in
an afternoon.

| Concern              | Decision                                                        |
|----------------------|-----------------------------------------------------------------|
| Language standard    | C++20 (concepts, `std::span`, `<bit>`, designated initializers) |
| Build system         | CMake ≥ 3.20 + CMake Presets                                     |
| Test framework       | GoogleTest (via `FetchContent`)                                 |
| On-disk format       | Fixed 4 KiB pages in one heap file (`*.db`)                      |
| I/O strategy         | `mmap` (POSIX) / `MapViewOfFile` (Windows) + buffered fallback  |
| Indexing             | Disk-backed **B+ Tree** (range + point lookups)                 |
| Caching              | **LRU** replacer driving a fixed-size **buffer pool**           |
| Query surface        | SQL subset: `CREATE TABLE`, `INSERT`, `SELECT … WHERE`          |
| Concurrency (v1)     | Single-writer, coarse latch (multi-threaded readers = backlog)  |

---

## 1.1 Project File Structure (Code + CI + Tools)

```text
4-Database-Engine/
├── CMakeLists.txt                 # Root build: library + CLI + tests wiring
├── CMakePresets.json              # Named configure/build/test presets
├── Dockerfile                     # Reproducible Linux/GCC build & test image
├── README.md                      # Quickstart, build matrix, layout
├── LICENSE                        # MIT
├── .gitignore
├── .clang-format                  # Code style (LLVM-derived)
├── .clang-tidy                    # Static analysis rules
├── .editorconfig
├── .env.example                   # Tunables consumed by CLI / tooling
│
├── docs/
│   ├── PROJECT-PLAN.md            # ← this file
│   ├── ARCHITECTURE.md            # Patterns, diagrams, data flow
│   └── TECH-NOTES.md              # CI/CD, testing, deployment, pitfalls
│
├── include/minidb/               # Public headers (the "API surface")
│   ├── common/
│   │   ├── config.hpp            # Page size, type aliases, sentinels
│   │   ├── byte_io.hpp          # Alignment-safe load/store for page bytes
│   │   ├── types.hpp           # TypeId, RID, Value (variant)
│   │   └── status.hpp         # Status / StatusOr error model
│   ├── storage/
│   │   ├── page.hpp           # 4 KiB frame + metadata
│   │   ├── header_page.hpp   # Superblock (page 0): magic + catalog root
│   │   ├── mmap_file.hpp    # RAII memory-mapped file (cross-platform)
│   │   ├── disk_manager.hpp # Page <-> file translation atop mmap
│   │   ├── lru_replacer.hpp # LRU victim selection (buffer pool policy)
│   │   ├── table_page.hpp  # Slotted page (variable-length tuples)
│   │   ├── table_heap.hpp # Page-chained tuple store over the buffer pool
│   │   └── buffer_pool_manager.hpp
│   ├── index/
│   │   ├── bplus_tree_page.hpp  # Leaf / internal node accessors
│   │   └── bplus_tree.hpp      # Page-backed index: insert/erase/point/range
│   ├── catalog/
│   │   ├── column.hpp           # (name, type, length)
│   │   ├── schema.hpp          # Ordered column set + tuple layout
│   │   ├── tuple_codec.hpp    # Row <-> bytes (null bitmap + values)
│   │   └── catalog.hpp        # Persisted table metadata registry (oids)
│   ├── parser/
│   │   ├── token.hpp            # Token kinds + source spans
│   │   ├── lexer.hpp          # SQL text -> tokens
│   │   ├── ast.hpp           # Statement/expression node types
│   │   └── parser.hpp       # Tokens -> AST (recursive descent)
│   ├── execution/
│   │   ├── result_set.hpp       # Column names + materialized rows
│   │   ├── plan_node.hpp       # Physical plan (SeqScan/IndexScan/...)
│   │   ├── cost_estimator.hpp # Heuristic cost model
│   │   └── executor.hpp      # Volcano-style plan execution
│   └── engine/
│       └── database.hpp          # Top-level façade: open() / execute()
│
├── src/                           # Implementations (mirror of include/)
│   ├── common/{status,types}.cpp
│   ├── storage/{mmap_file,disk_manager,lru_replacer,buffer_pool_manager,table_heap}.cpp
│   ├── index/bplus_tree.cpp
│   ├── catalog/{catalog,tuple_codec}.cpp
│   ├── parser/{lexer,parser}.cpp
│   ├── execution/{cost_estimator,executor}.cpp
│   ├── engine/database.cpp
│   └── cli/main.cpp               # REPL "frontend"
│
├── tests/                         # GoogleTest suites (45 cases, all passing)
│   ├── CMakeLists.txt
│   ├── test_lru_replacer.cpp
│   ├── test_buffer_pool_manager.cpp
│   ├── test_tuple_codec.cpp
│   ├── test_table_heap.cpp
│   ├── test_bplus_tree.cpp       # incl. randomized std::map oracle + persistence
│   ├── test_cost_estimator.cpp
│   ├── test_lexer.cpp
│   └── test_database.cpp         # end-to-end incl. reopen + EXPLAIN
│
├── benchmarks/
│   └── bench_bplus_tree.cpp       # Throughput micro-benchmark (standalone)
│
├── examples/
│   └── sample_queries.sql         # Demo workload for the CLI
│
├── migrations/
│   └── 0001_init_catalog.sql      # Bootstrap system catalog + demo table
│
├── cmake/
│   └── CompilerWarnings.cmake     # Reusable warnings-as-errors helper
│
├── scripts/
│   ├── build.ps1                  # Windows/MSVC convenience wrapper
│   └── build.sh                   # POSIX convenience wrapper
│
└── .github/
    ├── workflows/ci.yml           # lint -> build -> test matrix
    └── ISSUE_TEMPLATE/bug_report.md
```

### Layering rule (enforced by review + include hygiene)

```text
engine ─▶ execution ─▶ {catalog, index, parser}
                           │           │
                           ▼           ▼
                        storage  (buffer pool / disk / mmap)
                           │
                           ▼
                        common   (config / types / status)
```

A module may only include from **its own layer or below**. `common` includes
nothing project-specific. This keeps the dependency graph acyclic and makes each
layer independently testable.

---

## 1.2 Implementation TODO List

Legend: `[x]` done · `[~]` partial / reference stub · `[ ]` not started.

### Phase 1 — Foundation (high priority)

- [x] Repository scaffold, CMake, presets, CI, lint/format configs
- [x] `common/config.hpp` — page size, type aliases, sentinels
- [x] `common/types.hpp` + `Value` variant and `RID`
- [x] `common/status.hpp` — `Status` / `StatusOr<T>` (no exceptions on hot path)
- [x] `storage/lru_replacer` — **fully implemented + unit-tested**
- [x] `storage/page` — frame layout, pin/dirty metadata
- [~] `storage/mmap_file` — POSIX `mmap` + Windows `MapViewOfFile`, grow-on-demand `TODO`
- [~] `storage/disk_manager` — page read/write atop mmap; checksum `TODO`
- [x] `storage/buffer_pool_manager` — fetch/new/unpin/flush wired to LRU
- [x] `storage/mmap_file` — POSIX `mmap` + Windows `MapViewOfFile`, grow-on-demand
- [x] `storage/disk_manager` — page read/write atop mmap; superblock (page 0)
- [x] `storage/table_page` + `storage/table_heap` — slotted pages + page-chained heap
- [x] CI green on Linux (GCC) + Windows (MSVC)

### Phase 2 — Core features (medium priority)

- [x] `index/bplus_tree` — **page-backed** nodes via the buffer pool; insert with
      leaf/internal split, linked-leaf range scan, point delete (**unit-tested**,
      randomized `std::map` oracle incl. multi-level splits + persistence)
- [x] `catalog` — `Column`/`Schema`/`Catalog` **persisted** to a catalog heap;
      schemas + heap/index roots survive reopen
- [x] `catalog/tuple_codec` — null-bitmap tuple (de)serialization (**unit-tested**)
- [x] `parser/lexer` — SQL-subset tokenizer (**unit-tested**)
- [x] `parser/parser` — recursive descent for `CREATE/INSERT/SELECT[/EXPLAIN]`
- [x] `execution/executor` — SeqScan (heap) + IndexScan (B+ Tree) + Insert +
      CreateTable, all through the buffer pool; PK uniqueness enforced
- [x] `execution/cost_estimator` — heuristic model (**unit-tested**)
- [x] `engine/database` — façade wiring parse → plan → execute; durable `Flush`
- [x] `cli/main` — interactive REPL frontend

### Phase 3 — Polish & optimization (lower priority)

- [x] Page-split B+ Tree replacing the ordered-map reference (split done; node
      **merge** on delete is the remaining space optimization below)
- [x] `EXPLAIN` output (plan + estimated cost/rows)
- [x] Persistent system catalog (schemas survive reopen)
- [ ] B+ Tree node **merge / redistribute** on delete (lazy reclamation today)
- [ ] Write-ahead log (WAL) + crash recovery (ARIES-lite)
- [ ] MVCC / snapshot isolation for concurrent readers
- [ ] Page-level checksums + torn-write detection
- [ ] Cost model upgrade: histograms + sampling instead of flat selectivity
- [ ] Vectorized/columnar scan path for analytical queries
- [ ] Query plan visualizer; streaming (non-materialized) scan iterators
- [ ] Fuzzing harness (libFuzzer) for the parser; ASan/UBSan CI lane
- [ ] Benchmark suite wired into CI with regression gates

---

## 2. Milestones

| Milestone | Exit criteria                                                            |
|-----------|--------------------------------------------------------------------------|
| M1 Storage| Buffer pool + LRU + disk manager pass tests; pages survive reopen         |
| M2 Index  | B+ Tree point + range queries correct under randomized insert/delete      |
| M3 SQL    | `CREATE/INSERT/SELECT … WHERE` works end-to-end through the CLI            |
| M4 Cost   | Planner picks IndexScan vs SeqScan correctly on a selectivity sweep        |
| M5 Hardening | ASan/UBSan clean; fuzzed parser; documented recovery story            |

---

## 3. Risks & Mitigations

| Risk                                   | Likelihood | Mitigation                                              |
|----------------------------------------|------------|---------------------------------------------------------|
| `mmap` portability (Win vs POSIX)      | High       | Single RAII wrapper, `#ifdef` isolated, buffered fallback |
| Growing an active mmap region          | Medium     | Pre-size file in chunks; remap on growth; document limits |
| B+ Tree split/merge correctness        | High       | Property-based randomized tests vs `std::map` oracle      |
| Buffer pool eviction of pinned pages   | Medium     | Pin-count invariants asserted; LRU only sees unpinned     |
| Scope creep (becoming "a real DB")     | High       | Phase gates; non-goals are explicit (see below)           |

### Non-goals (v1)

Networking/clients, SQL completeness (joins, aggregates, subqueries),
distributed consensus, full ACID durability, and authentication are **out of
scope**. They are deliberately deferred so the core storage/index/execution loop
stays legible.
