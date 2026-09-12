# MiniDB

An educational, embeddable **database engine** in modern **C++20**. It demonstrates
the internals of a real storage engine in a codebase small enough to read:

- **B+ Tree** index for fast point lookups and range queries
- **Memory-mapped (mmap) I/O** with a cross-platform RAII wrapper
- An **LRU**-managed **buffer pool** over fixed 4 KiB pages
- A **SQL subset** parser/executor (`CREATE TABLE`, `INSERT`, `SELECT … WHERE`)
- **Cost-based** planning that chooses sequential vs. index scans

> **⚠️ No networking — MiniDB is embedded, not a server.** Like SQLite, it runs
> **in your process**: there is **no network listener, no client/server protocol,
> no host/port, and no driver or connection string**. You "connect" by opening the
> database file directly — from the CLI (`minidb app.db`) or the C++ API
> (`minidb::Database::Open("app.db")`).

> Status: working engine. Tuples and indexes live on **disk pages** accessed
> through the LRU buffer pool over an mmap'd file; the system catalog persists, so
> tables and data **survive a reopen**. CREATE / INSERT / SELECT … WHERE and
> EXPLAIN run end-to-end, and a 45-case GoogleTest suite passes (incl. a
> randomized B+ Tree oracle with multi-level splits and cross-reopen persistence).
> Deferred to future work (see [`docs/PROJECT-PLAN.md`](docs/PROJECT-PLAN.md), Phase 3): B+ Tree node
> *merge* on delete, WAL/recovery, MVCC, and page checksums.

---

## Quickstart

### Build & test

```bash
# POSIX
./scripts/build.sh                 # or: cmake -S . -B build && cmake --build build && ctest --test-dir build
```

```powershell
# Windows (MSVC) — from a Developer PowerShell, or let the script find VS
./scripts/build.ps1
```

With CMake presets:

```bash
cmake --preset default
cmake --build --preset default
ctest --preset default
```

### Run the shell

```bash
./build/minidb demo.db                 # single-config generators (Ninja/Make)
# Windows multi-config (Visual Studio): build\Release\minidb.exe demo.db
```

```text
MiniDB shell (file: demo.db). Type .help for help.
CREATE TABLE users (id INT, name VARCHAR(32));
Table 'users' created (oid=1).
INSERT INTO users VALUES (1,'Ada'),(2,'Linus');
INSERT 2 row(s) into 'users'.
SELECT id, name FROM users WHERE id = 2;
+----+-------+
| id | name  |
+----+-------+
| 2  | Linus |
+----+-------+
1 row(s)  [scan=SeqScan est_cost=1.02 rows=1]
```

Batch mode: `./build/minidb demo.db < examples/sample_queries.sql`.
Bootstrap a demo schema: `./build/minidb app.db < migrations/0001_init_catalog.sql`.

### With Docker

```bash
docker compose build           # compiles AND runs the full test suite
docker compose run --rm shell  # interactive SQL shell on a persisted volume
```

---

## Architecture at a glance

```text
engine ─▶ execution ─▶ {catalog, index, parser} ─▶ storage ─▶ common
```

A strict layered design where each layer only depends on the ones below it. The
buffer pool is the single choke point through which all durable data passes.
Full details, diagrams, and rationale: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

| Area | Header | Implemented? |
|------|--------|--------------|
| Error model | `common/status.hpp` | ✅ |
| Types / values | `common/types.hpp` | ✅ |
| mmap file | `storage/mmap_file.hpp` | ✅ (POSIX + Windows) |
| Disk manager | `storage/disk_manager.hpp` | ✅ |
| LRU replacer | `storage/lru_replacer.hpp` | ✅ tested |
| Buffer pool | `storage/buffer_pool_manager.hpp` | ✅ tested |
| Table heap | `storage/table_heap.hpp` | ✅ tested (slotted pages) |
| Tuple codec | `catalog/tuple_codec.hpp` | ✅ tested |
| B+ Tree | `index/bplus_tree.hpp` | ✅ tested (page-backed, split) |
| Catalog | `catalog/catalog.hpp` | ✅ persisted |
| Lexer | `parser/lexer.hpp` | ✅ tested |
| Parser | `parser/parser.hpp` | ✅ (subset + EXPLAIN) |
| Cost estimator | `execution/cost_estimator.hpp` | ✅ tested |
| Executor | `execution/executor.hpp` | ✅ tested (heap + index) |
| Database façade | `engine/database.hpp` | ✅ persistent |

---

## Repository layout

```text
include/minidb/   public headers (the API surface)
src/              implementations, mirrors include/
src/cli/          interactive shell (the frontend)
tests/            GoogleTest suites
docs/             PROJECT-PLAN, ARCHITECTURE, TECH-NOTES
examples/         sample SQL workload
migrations/       schema bootstrap scripts
cmake/, scripts/  build helpers
```

## Documentation

- [`docs/PROJECT-PLAN.md`](docs/PROJECT-PLAN.md) — structure, phased TODO, risks
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — pattern, components, data flow, security
- [`docs/TECH-NOTES.md`](docs/TECH-NOTES.md) — CI/CD, testing, deployment, pitfalls

## Requirements

- A C++20 compiler (GCC 12+, Clang 15+, or MSVC 19.3x+)
- CMake ≥ 3.20
- Internet access on first configure (GoogleTest via `FetchContent`), or a
  pre-populated CMake cache for offline builds
- No other runtime dependencies. The only third-party code (GoogleTest) is
  test-only and pinned in `CMakeLists.txt`.

## Configuration

MiniDB runs with zero config. The one runtime knob read today is
`MINIDB_BUFFER_POOL_PAGES` (buffer-pool frames); see [`.env.example`](.env.example)
for the full (and honestly-labeled) surface. **No secrets** are involved — the
engine is embedded and holds none.

## Security scope

MiniDB is an **embedded, single-process** library, so the usual web concerns do
not apply: there is **no network surface, no authentication, no sessions, no
HTML** → no SQL-injection-into-a-second-interpreter, XSS, CSRF, password storage,
or HTTPS to configure. The untrusted boundary is the SQL parser, which is
hardened by: a typed AST (no `eval`), bounds-checked page/tuple deserialization,
VARCHAR-length and INT-range validation on insert, and a single-quote/comment
-aware statement splitter. File confidentiality is delegated to OS permissions /
disk encryption. See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) §2.5.

## Troubleshooting

| Symptom | Cause / fix |
|---------|-------------|
| `find_package`/`FetchContent` fails on first configure | No network for the GoogleTest clone. Build with `-DMINIDB_BUILD_TESTS=OFF`, or pre-populate the CMake cache. |
| Windows: `MapViewOfFile`/sharing-violation when opening a `*.db` | The file is opened single-writer (`FILE_SHARE_READ`). Close the other handle/process first — one process opens a database at a time. |
| `Error: SYNTAX_ERROR` in the shell | Statements must end with `;`. Multi-line is fine; check for an unterminated string (`'`). |
| `IO_ERROR: ...` on a query | The data file is unreadable/locked, or a different process holds it open. |
| Changes lost after exit | Call `Flush()` (the CLI does this on `.exit`/EOF) before the process ends; a hard kill mid-write is not yet crash-safe (WAL is Phase 3). |
| MSVC `/WX` build fails on `C4996` | Build through the provided CMake — it defines `_CRT_SECURE_NO_WARNINGS` for MSVC targets. |

## License

MIT — see [`LICENSE`](LICENSE).
