# Portable Process Monitor — Project Plan

> **Project:** Portable Process Monitor (`ppmon`)
> **Language / Platform:** C11, Windows (x64 / ARM64)
> **Core APIs:** Win32, PSAPI, PDH, `NtQuerySystemInformation`, WinSock2
> **Build / Test / CI:** CMake + CTest, GitHub Actions
> **Last updated:** 2026-06-16

A single-binary, dependency-light console tool that polls the Windows kernel and
performance subsystems on a fixed interval and reports per-process CPU, memory,
and I/O metrics. It exports time-series data to CSV and raises threshold-based
alerts to the console. An optional WinSock2 TCP listener streams the same metrics
to remote collectors.

---

## 1.1 Project File Structure

The layout follows the conventional separation used by portable C projects:
public headers in `include/`, translation units in `src/`, isolated unit tests in
`tests/`, and build orchestration at the root. There is no "frontend/backend"
split in the web sense — instead the natural seams are the **collection layer**
(kernel/PSAPI/PDH), the **domain layer** (metrics model, alerting, CSV), and the
**presentation layer** (console UI + optional network server).

```
2Portable-Process-Monitor/
├── .github/
│   └── workflows/
│       └── ci.yml                  # GitHub Actions: lint → build → test → package
├── cmake/
│   ├── CompilerWarnings.cmake      # Centralised warning flags (/W4 /WX, analyze)
│   └── Packaging.cmake             # CPack / ZIP artifact rules
├── config/
│   └── ppmon.ini.example           # Sample runtime configuration
├── docs/
│   ├── PROJECT-PLAN.md             # ← this file
│   ├── ARCHITECTURE.md             # Patterns, diagrams, data flow
│   └── TECH-NOTES.md               # CI/CD, testing, deployment, pitfalls
├── include/
│   └── ppmon/                      # Public API surface (one header per module)
│       ├── ppmon.h                 # Umbrella header + version macros
│       ├── config.h                # Config struct + INI/CLI parsing
│       ├── log.h                   # Levelled logging facade
│       ├── timer.h                 # QueryPerformanceCounter polling clock
│       ├── process_enum.h          # NtQuerySystemInformation enumeration
│       ├── metrics.h               # Per-process CPU/mem/IO sampling (PSAPI)
│       ├── pdh_counters.h          # System-wide PDH counters
│       ├── sample_store.h          # Ring buffer of samples / delta computation
│       ├── alerting.h              # Threshold rules + evaluation
│       ├── csv_export.h            # CSV writer + schema definition
│       ├── console_ui.h            # Terminal rendering
│       └── net_server.h            # WinSock2 metric streaming server
├── src/
│   ├── main.c                      # Entry point, arg parsing, lifecycle
│   ├── config.c
│   ├── log.c
│   ├── timer.c
│   ├── process_enum.c
│   ├── metrics.c
│   ├── pdh_counters.c
│   ├── sample_store.c
│   ├── alerting.c
│   ├── csv_export.c
│   ├── console_ui.c
│   └── net_server.c
├── tests/
│   ├── CMakeLists.txt              # Registers each test with CTest
│   ├── test_config.c               # INI/CLI parsing edge cases
│   ├── test_alerting.c             # Threshold evaluation logic
│   ├── test_csv_export.c           # CSV escaping / schema stability
│   └── test_sample_store.c         # Ring buffer + CPU delta math
├── scripts/
│   ├── build.ps1                   # Configure + build wrapper
│   └── format.ps1                  # Run clang-format over the tree
├── .clang-format                   # Code style (LLVM-derived)
├── .clang-tidy                     # Static analysis rules
├── .editorconfig                   # Editor-agnostic whitespace rules
├── .gitignore
├── CMakeLists.txt                  # Root build definition
├── LICENSE
└── README.md
```

### Layer / dependency rules

| Layer            | Modules                                   | May depend on        |
|------------------|-------------------------------------------|----------------------|
| Presentation     | `console_ui`, `net_server`                | Domain, Platform     |
| Domain           | `metrics`, `alerting`, `csv_export`, `sample_store` | Platform, Util |
| Collection (Platform) | `process_enum`, `pdh_counters`, `timer` | Util            |
| Util             | `config`, `log`                           | (none)               |

Dependencies point **downward only**. The domain layer never calls Win32
directly — collection modules translate raw OS structures into plain
`ppmon_sample_t` records, keeping the testable core free of platform headers.

---

## 1.2 Implementation TODO List

> **Status: implemented.** All Phase 1–3 items are complete and verified (clean
> `/W4 /WX` build, 4 unit tests + 1 live-OS integration test passing, portable
> ZIP produced via CPack). The single deliberately-deferred item is the optional
> ETW enrichment, left unchecked below. See [TECH-NOTES.md](./TECH-NOTES.md).

### ☑ Phase 1 — Foundation (high priority)
- [x] Initialise CMake project (`C11`, `/W4 /WX`, `NDEBUG` release config).
- [x] Wire CTest with at least one trivial passing test (CI smoke gate).
- [x] Implement `log` (levelled, thread-safe, `stderr` sink).
- [x] Implement `timer` over `QueryPerformanceCounter` with drift-free interval scheduling.
- [x] Implement `config`: defaults → INI file → CLI overrides precedence chain.
- [x] Implement `process_enum` using `NtQuerySystemInformation(SystemProcessInformation)` with retry-on-`STATUS_INFO_LENGTH_MISMATCH`.
- [x] Stand up GitHub Actions CI (build + test on `windows-latest`).

### ☑ Phase 2 — Core features (medium priority)
- [x] Implement `metrics`: per-process CPU% via `GetProcessTimes` deltas, memory via `GetProcessMemoryInfo` (PSAPI), I/O via `GetProcessIoCounters`.
- [x] Implement `pdh_counters`: system-wide CPU, memory, disk queue via PDH (`PdhAddEnglishCounter`).
- [x] Implement `sample_store`: per-process slots, previous/current delta bookkeeping, PID-reuse detection, generation-based eviction.
- [x] Implement `alerting`: threshold rules + hysteresis primitive, plus a stateful per-process engine.
- [x] Implement `csv_export`: stable schema, RFC-4180 escaping, append.
- [x] Implement `console_ui`: sortable top-N table, refresh-in-place VT rendering.
- [x] Implement the main polling loop tying timer → collect → store → alert → render → export.

### ☑ Phase 3 — Polish & optimisation (lower priority)
- [x] Implement `net_server` (WinSock2): non-blocking TCP, line-delimited stream, optional token auth.
- [x] Add graceful shutdown (Ctrl+C / `SetConsoleCtrlHandler`), flush CSV every cycle and on exit.
- [x] Reduce per-poll allocations (reused enumeration buffer + reused row/event/frame buffers).
- [x] Add `--privilege` (SeDebugPrivilege) path for full process visibility.
- [ ] Optional ETW-based I/O enrichment behind a feature flag. *(deferred — optional)*
- [x] CPack packaging: portable ZIP with config sample + README.
- [x] `.clang-tidy` in CI; code-coverage gate (OpenCppCoverage) documented in TECH-NOTES.
```
